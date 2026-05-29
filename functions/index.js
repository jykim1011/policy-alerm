const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.onNewPolicy = onDocumentCreated(
  "new_policies/{policyId}",
  async (event) => {
    const policy = event.data.data();
    const policyId = event.params.policyId;
    const db = getFirestore();

    // 구독 카테고리가 일치하는 유저 조회
    const usersSnap = await db
      .collection("users")
      .where("subscribed_categories", "array-contains", policy.subcategory)
      .get();

    const tokens = [];
    usersSnap.forEach((doc) => {
      const user = doc.data();
      const schedule = user.notification_schedule || "both";
      // 유저의 알림 시간 설정과 배치 매칭
      if (schedule === "both" || schedule === policy.batch) {
        if (user.fcm_token) {
          tokens.push(user.fcm_token);
        }
      }
    });

    // Firestore 트리거 문서 삭제 (중복 방지)
    await event.data.ref.delete();

    if (tokens.length === 0) {
      console.log(`No matching users for policy: ${policyId}`);
      return;
    }

    // FCM multicast 발송 (최대 500개씩)
    const chunkSize = 500;
    for (let i = 0; i < tokens.length; i += chunkSize) {
      const chunk = tokens.slice(i, i + chunkSize);
      const message = {
        notification: {
          title: `새 ${policy.category} 정책`,
          body: policy.title,
        },
        data: {
          policy_id: policyId,
          category: policy.category,
          subcategory: policy.subcategory,
        },
        tokens: chunk,
      };

      const response = await getMessaging().sendEachForMulticast(message);
      console.log(
        `Sent ${response.successCount}/${chunk.length} notifications for ${policyId}`
      );

      // 만료된 토큰 정리
      const expiredTokens = [];
      response.responses.forEach((resp, idx) => {
        if (
          !resp.success &&
          resp.error?.code === "messaging/registration-token-not-registered"
        ) {
          expiredTokens.push(chunk[idx]);
        }
      });

      if (expiredTokens.length > 0) {
        const batch = db.batch();
        const usersWithExpiredSnap = await db
          .collection("users")
          .where("fcm_token", "in", expiredTokens)
          .get();
        usersWithExpiredSnap.forEach((doc) => {
          batch.update(doc.ref, { fcm_token: null });
        });
        await batch.commit();
      }
    }
  }
);
