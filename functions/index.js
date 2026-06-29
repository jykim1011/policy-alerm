const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { computeUserDigest, formatBreakdown, formatDigestBody } = require("./digest");

initializeApp();

const FCM_CHANNEL_ID = "policy_alerts_v2";

// 야간 방해금지 구간(한국시간 기준). 시작 ≤ hour < 24 또는 0 ≤ hour < 끝.
// 이 시간대 정책은 즉시 푸시하지 않고 쌓아 두었다가 morningDigest(매일 09:00 KST)가
// 한 번에 알린다.
const QUIET_START_KST = 22; // 밤 10시
const QUIET_END_KST = 9; // 아침 9시

/** 현재가 KST 야간 방해금지 시간대(22:00~09:00)인지 반환한다. */
function isQuietHoursKst(now = new Date()) {
  // 서버는 UTC로 동작하므로 +9시간 해 KST 시각(0~23)을 구한다.
  const kstHour = (now.getUTCHours() + 9) % 24;
  return kstHour >= QUIET_START_KST || kstHour < QUIET_END_KST;
}

exports.onNewPolicyBatch = onDocumentCreated(
  "new_policy_batches/{runId}",
  async (event) => {
    const data = event.data.data();
    const policies = Array.isArray(data.policies) ? data.policies : [];
    const db = getFirestore();

    console.log(`[onNewPolicyBatch] START runId=${event.params.runId} policies=${policies.length}`);

    if (policies.length === 0) {
      await event.data.ref.delete();
      return;
    }

    // 배치 정책들의 category·subcategory 합집합으로 구독자 조회.
    // array-contains-any 는 값 30개 제한 → 30개씩 청크로 나눠 조회 후 doc.id로 머지(중복 제거).
    const matchValues = [
      ...new Set(policies.flatMap((p) => [p.subcategory, p.category]).filter(Boolean)),
    ];
    const usersById = new Map();
    for (let i = 0; i < matchValues.length; i += 30) {
      const chunk = matchValues.slice(i, i + 30);
      const snap = await db
        .collection("users")
        .where("subscribed_categories", "array-contains-any", chunk)
        .get();
      snap.forEach((doc) => usersById.set(doc.id, doc));
    }
    const userDocs = [...usersById.values()];
    console.log(`[onNewPolicyBatch] matched ${userDocs.length} subscribers`);

    // 사용자별 매칭 다이제스트 산출(구독 분야에 매칭되는 정책만).
    const digests = userDocs
      .map((doc) => ({
        doc,
        user: doc.data(),
        ...computeUserDigest(doc.data().subscribed_categories, policies),
      }))
      .filter((d) => d.count > 0);

    // 알림함 기록: (사용자 × 매칭 정책)마다 notifications/{policyId}. 배치 500개 제한 청크.
    const notifWrites = [];
    digests.forEach((d) => {
      d.matched.forEach((p) => {
        notifWrites.push({ userRef: d.doc.ref, policy: p });
      });
    });
    for (let i = 0; i < notifWrites.length; i += 500) {
      const batch = db.batch();
      notifWrites.slice(i, i + 500).forEach(({ userRef, policy }) => {
        batch.set(userRef.collection("notifications").doc(policy.id), {
          title: policy.title,
          category: policy.category,
          subcategory: policy.subcategory,
          received_at: FieldValue.serverTimestamp(),
          read: false,
        });
      });
      await batch.commit();
    }
    console.log(`[onNewPolicyBatch] wrote ${notifWrites.length} notification records`);

    // 트리거 문서 삭제 (중복 방지). 현 설계와 동일 수준의 at-least-once 수용.
    await event.data.ref.delete();

    // 야간 방해금지(KST 22:00~09:00): 즉시 푸시하지 않고 사용자별 누적만. morningDigest가 발송.
    if (isQuietHoursKst()) {
      for (let i = 0; i < digests.length; i += 500) {
        const batch = db.batch();
        digests.slice(i, i + 500).forEach((d) => {
          const update = { overnight_pending: FieldValue.increment(d.count) };
          for (const [cat, n] of Object.entries(d.breakdown)) {
            update[`overnight_breakdown.${cat}`] = FieldValue.increment(n);
          }
          batch.update(d.doc.ref, update);
        });
        await batch.commit();
      }
      console.log(`[onNewPolicyBatch] Quiet hours — deferred to morning digest (${digests.length} users).`);
      return;
    }

    // 주간: 사용자별 푸시 1건. 내용이 사용자마다 다르므로 sendEach(토큰별 메시지 배열).
    const messages = [];
    const messageTokens = [];
    digests.forEach((d) => {
      if (!d.user.fcm_token) return;
      messageTokens.push(d.user.fcm_token);
      if (d.count === 1) {
        const p = d.matched[0];
        const title = `새 ${p.category} 정책`;
        messages.push({
          token: d.user.fcm_token,
          notification: { title, body: p.title },
          data: {
            policy_id: p.id,
            category: p.category,
            subcategory: p.subcategory,
            title,
            body: p.title,
          },
          android: { priority: "high", notification: { channelId: FCM_CHANNEL_ID } },
        });
      } else {
        messages.push({
          token: d.user.fcm_token,
          notification: {
            title: `새 정책 ${d.count}건`,
            body: formatDigestBody(d.matched, d.breakdown),
          },
          data: { open_tab: "history" },
          android: { priority: "high", notification: { channelId: FCM_CHANNEL_ID } },
        });
      }
    });

    if (messages.length === 0) {
      console.log(`[onNewPolicyBatch] No tokens to send.`);
      return;
    }

    const expiredTokens = [];
    for (let i = 0; i < messages.length; i += 500) {
      const chunk = messages.slice(i, i + 500);
      const tokenChunk = messageTokens.slice(i, i + 500);
      let response;
      try {
        response = await getMessaging().sendEach(chunk);
      } catch (err) {
        console.error(`[onNewPolicyBatch] sendEach THREW for chunk ${i}: ${err.message} — continuing`);
        continue;
      }
      console.log(`[onNewPolicyBatch] Sent ${response.successCount}/${chunk.length}, failures=${response.failureCount}`);
      response.responses.forEach((resp, idx) => {
        if (!resp.success && resp.error?.code === "messaging/registration-token-not-registered") {
          expiredTokens.push(tokenChunk[idx]);
        }
      });
    }

    // 만료 토큰 정리 (Firestore `in` 은 최대 30개).
    for (let j = 0; j < expiredTokens.length; j += 30) {
      const tokenChunk = expiredTokens.slice(j, j + 30);
      const expiredSnap = await db.collection("users").where("fcm_token", "in", tokenChunk).get();
      const cleanup = db.batch();
      expiredSnap.forEach((doc) => cleanup.update(doc.ref, { fcm_token: null }));
      await cleanup.commit();
    }

    console.log(`[onNewPolicyBatch] DONE runId=${event.params.runId}`);
  }
);

// 매일 KST 09:00 — 야간(22:00~09:00)에 쌓인 정책을 사용자별로 한 번에 알린다.
// onNewPolicy 가 야간에 올려 둔 overnight_pending 카운트를 읽어 "밤사이 N건" 푸시를
// 보내고 카운트를 비운다(필드 삭제).
exports.morningDigest = onSchedule(
  { schedule: "0 9 * * *", timeZone: "Asia/Seoul" },
  async () => {
    const db = getFirestore();
    const snap = await db
      .collection("users")
      .where("overnight_pending", ">", 0)
      .get();

    console.log(`[morningDigest] START — ${snap.size} users with pending overnight policies`);
    if (snap.empty) return;

    // 토큰이 있는 사용자에게만 보낼 메시지를 만든다. 카운트 리셋은 토큰 유무와 무관하게 모두.
    const messages = [];
    const messageTokens = [];
    const resetRefs = [];
    snap.forEach((doc) => {
      resetRefs.push(doc.ref);
      const user = doc.data();
      const count = user.overnight_pending || 0;
      if (user.fcm_token && count > 0) {
        // 야간 누적은 제목을 보관하지 않으므로(밤새 배열 증가 write 회피) 분야 카운트 형식만 쓴다.
        // overnight_breakdown 이 있으면 "(부동산 3·고용 2)"를 덧붙이고, 없으면(구버전) 건수만.
        const breakdown = user.overnight_breakdown || {};
        const detail = Object.keys(breakdown).length > 0 ? ` (${formatBreakdown(breakdown)})` : "";
        messageTokens.push(user.fcm_token);
        messages.push({
          token: user.fcm_token,
          notification: {
            title: "정책 알리미",
            body: `밤사이 새 정책 ${count}건${detail}`,
          },
          // 묶음 알림은 특정 정책이 아니므로, 탭하면 앱의 알림 탭으로 연다.
          data: { open_tab: "history" },
          android: {
            priority: "high",
            notification: { channelId: FCM_CHANNEL_ID },
          },
        });
      }
    });

    // 푸시 발송 (sendEach 는 메시지 배열, 청크당 최대 500건).
    // 한 청크가 throw 해도 중단하지 않는다. 중단하면 아래 리셋이 실행되지 않아,
    // 다음 실행/재시도 때 이미 받은 사용자에게 디지트가 중복 발송된다.
    const expiredTokens = [];
    for (let i = 0; i < messages.length; i += 500) {
      const chunk = messages.slice(i, i + 500);
      const tokenChunk = messageTokens.slice(i, i + 500);
      let response;
      try {
        response = await getMessaging().sendEach(chunk);
      } catch (err) {
        console.error(`[morningDigest] sendEach THREW for chunk ${i}: ${err.message} — continuing`);
        continue;
      }
      console.log(`[morningDigest] Sent ${response.successCount}/${chunk.length}, failures=${response.failureCount}`);
      response.responses.forEach((resp, idx) => {
        if (!resp.success && resp.error?.code === "messaging/registration-token-not-registered") {
          expiredTokens.push(tokenChunk[idx]);
        }
      });
    }

    // overnight_pending 리셋 (필드 삭제 → 다음 쿼리에서 제외). 중복 발송 방지의 핵심이므로
    // 만료 토큰 정리(부가 작업)보다 먼저, 발송 성공/실패와 무관하게 항상 수행한다.
    for (let i = 0; i < resetRefs.length; i += 500) {
      const batch = db.batch();
      resetRefs.slice(i, i + 500).forEach((ref) => {
        batch.update(ref, {
          overnight_pending: FieldValue.delete(),
          overnight_breakdown: FieldValue.delete(),
        });
      });
      await batch.commit();
    }

    // 만료 토큰 정리 (Firestore `in` 은 최대 30개) — 실패해도 이미 리셋됐으므로 안전.
    for (let j = 0; j < expiredTokens.length; j += 30) {
      const tokenChunk = expiredTokens.slice(j, j + 30);
      const expiredSnap = await db.collection("users").where("fcm_token", "in", tokenChunk).get();
      const batch = db.batch();
      expiredSnap.forEach((doc) => batch.update(doc.ref, { fcm_token: null }));
      await batch.commit();
    }

    console.log(`[morningDigest] DONE — pushed to ${messages.length}, reset ${resetRefs.length}`);
  }
);
