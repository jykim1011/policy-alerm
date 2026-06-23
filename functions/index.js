const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

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

exports.onNewPolicy = onDocumentCreated(
  "new_policies/{policyId}",
  async (event) => {
    const policy = event.data.data();
    // event.params.policyId 는 gen2 트리거에서 한글 등 비ASCII 문서 ID를 모지바케로
    // 깨뜨린다(firebase-functions#1459). 그 깨진 값을 policy_id 로 쓰면 앱이
    // policies/{id}.json 을 404 로 받는다. 문서 본문(event.data.data())은 깨지지 않으므로
    // 파이프라인이 본문에 저장한 id 를 단일 소스로 사용한다(구버전 문서 대비 params 폴백).
    const policyId = policy.id || event.params.policyId;
    const db = getFirestore();

    console.log(`[onNewPolicy] START policyId=${policyId} category=${policy.category} subcategory=${policy.subcategory}`);

    // subcategory가 undefined/null이면 Firestore array-contains-any가 예외를 던지므로 필터링한다.
    const matchValues = [...new Set([policy.subcategory, policy.category].filter(Boolean))];
    if (matchValues.length === 0) {
      console.error(`[onNewPolicy] No valid matchValues for policy: ${policyId}`);
      await event.data.ref.delete();
      return;
    }

    console.log(`[onNewPolicy] Querying users with subscribed_categories array-contains-any ${JSON.stringify(matchValues)}`);
    const usersSnap = await db
      .collection("users")
      .where("subscribed_categories", "array-contains-any", matchValues)
      .get();

    console.log(`[onNewPolicy] Found ${usersSnap.size} matching users for policy: ${policyId}`);

    // 구독자 각자의 알림함(users/{uid}/notifications/{policyId})에 기록한다.
    // 앱이 푸시를 잡았는지/사용자가 탭했는지와 무관하게 알림 탭에서 항상 보이게 하는 단일 소스.
    // Firestore 배치는 최대 500개 작업이므로 청크 단위로 커밋한다.
    for (let i = 0; i < usersSnap.docs.length; i += 500) {
      const notifBatch = db.batch();
      usersSnap.docs.slice(i, i + 500).forEach((doc) => {
        notifBatch.set(
          doc.ref.collection("notifications").doc(policyId),
          {
            title: policy.title,
            category: policy.category,
            subcategory: policy.subcategory,
            received_at: FieldValue.serverTimestamp(),
            read: false,
          }
        );
      });
      await notifBatch.commit();
    }
    console.log(`[onNewPolicy] Wrote notifications to ${usersSnap.size} users' subcollections`);

    const tokens = [];
    const usersWithNoToken = [];
    usersSnap.forEach((doc) => {
      const user = doc.data();
      if (user.fcm_token) {
        tokens.push(user.fcm_token);
      } else {
        usersWithNoToken.push(doc.id);
      }
    });

    console.log(`[onNewPolicy] FCM tokens: ${tokens.length} valid, ${usersWithNoToken.length} missing (uid list: ${JSON.stringify(usersWithNoToken)})`);

    // Firestore 트리거 문서 삭제 (중복 방지)
    await event.data.ref.delete();

    // 야간 방해금지(KST 22:00~09:00): 즉시 푸시하지 않고, 구독자별로 밤사이 누적 건수
    // (overnight_pending)를 올려 둔다. 알림함(users/{uid}/notifications)에는 위에서 이미
    // 기록했고, 쌓인 건수는 morningDigest(매일 09:00 KST)가 한 번에 푸시로 알린다.
    if (isQuietHoursKst()) {
      for (let i = 0; i < usersSnap.docs.length; i += 500) {
        const pendingBatch = db.batch();
        usersSnap.docs.slice(i, i + 500).forEach((doc) => {
          pendingBatch.update(doc.ref, {
            overnight_pending: FieldValue.increment(1),
          });
        });
        await pendingBatch.commit();
      }
      console.log(`[onNewPolicy] Quiet hours (KST) — deferred ${policyId} to morning digest (${usersSnap.size} users).`);
      return;
    }

    if (tokens.length === 0) {
      console.log(`[onNewPolicy] No FCM tokens to send for policy: ${policyId}. Users exist but have no token.`);
      return;
    }

    const chunkSize = 500;
    for (let i = 0; i < tokens.length; i += chunkSize) {
      const chunk = tokens.slice(i, i + chunkSize);
      const message = {
        // notification: OS 레벨에서 앱이 종료/백그라운드여도 알림 표시 보장 (OEM 배터리 최적화 우회)
        notification: {
          title: `새 ${policy.category} 정책`,
          body: policy.title,
        },
        // data: onMessageReceived(포그라운드) 및 탭 인텐트(백그라운드/종료)에서 DB 저장용
        data: {
          policy_id: policyId,
          category: policy.category,
          subcategory: policy.subcategory,
          title: `새 ${policy.category} 정책`,
          body: policy.title,
        },
        android: {
          priority: "high",
          notification: { channelId: FCM_CHANNEL_ID },
        },
        tokens: chunk,
      };

      let response;
      try {
        response = await getMessaging().sendEachForMulticast(message);
      } catch (err) {
        console.error(`[onNewPolicy] sendEachForMulticast THREW for chunk ${i}: ${err.message}`);
        throw err;
      }

      console.log(
        `[onNewPolicy] Sent ${response.successCount}/${chunk.length} notifications for ${policyId}. ` +
        `failureCount=${response.failureCount}`
      );

      const expiredTokens = [];
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          console.warn(`[onNewPolicy] FCM send failed for token[${idx}]: code=${resp.error?.code} msg=${resp.error?.message}`);
          if (resp.error?.code === "messaging/registration-token-not-registered") {
            expiredTokens.push(chunk[idx]);
          }
        }
      });

      if (expiredTokens.length > 0) {
        console.log(`[onNewPolicy] Clearing ${expiredTokens.length} expired tokens from Firestore`);
        // Firestore `in` 연산자는 최대 30개 값만 허용한다.
        const IN_LIMIT = 30;
        for (let j = 0; j < expiredTokens.length; j += IN_LIMIT) {
          const tokenChunk = expiredTokens.slice(j, j + IN_LIMIT);
          const usersWithExpiredSnap = await db
            .collection("users")
            .where("fcm_token", "in", tokenChunk)
            .get();
          const cleanupBatch = db.batch();
          usersWithExpiredSnap.forEach((doc) => {
            cleanupBatch.update(doc.ref, { fcm_token: null });
          });
          await cleanupBatch.commit();
        }
      }
    }

    console.log(`[onNewPolicy] DONE for policyId=${policyId}`);
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
        messageTokens.push(user.fcm_token);
        messages.push({
          token: user.fcm_token,
          notification: {
            title: "정책 알리미",
            body: `밤사이 새 정책 ${count}건이 도착했어요`,
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
        batch.update(ref, { overnight_pending: FieldValue.delete() });
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
