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

    const usersSnap = await db
      .collection("users")
      .where("subscribed_categories", "array-contains", policy.subcategory)
      .get();

    const tokens = [];
    usersSnap.forEach((doc) => {
      const user = doc.data();
      if (user.fcm_token) {
        tokens.push(user.fcm_token);
      }
    });

    // Firestore 트리거 문서 삭제 (중복 방지)
    await event.data.ref.delete();

    if (tokens.length === 0) {
      console.log(`No matching users for policy: ${policyId}`);
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
        android: { priority: "high" },
        tokens: chunk,
      };

      const response = await getMessaging().sendEachForMulticast(message);
      console.log(
        `Sent ${response.successCount}/${chunk.length} notifications for ${policyId}`
      );

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
