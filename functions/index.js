const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
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
