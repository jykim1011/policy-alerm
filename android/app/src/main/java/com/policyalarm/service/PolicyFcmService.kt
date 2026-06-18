package com.policyalarm.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.policyalarm.MainActivity
import com.policyalarm.R
import com.policyalarm.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PolicyFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // 포그라운드 수신 시 알림 표시만 담당한다. 알림 기록은 Cloud Function이 Firestore에
        // 써 두는 것을 단일 소스로 사용하므로 여기서 DB에 저장하지 않는다.
        val policyId = message.data["policy_id"] ?: return
        val title = message.data["title"] ?: "새 정책"
        val body = message.data["body"] ?: ""
        showNotification(policyId, title, body)
    }

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                UserRepository().updateFcmToken(token)
            } catch (_: Exception) {
                // 로그인 전이면 무시 — 로그인 시 토큰 등록
            }
        }
    }

    private fun showNotification(policyId: String, title: String, body: String) {
        val channelId = "policy_alerts"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId, "정책 알림", NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("policy_id", policyId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, policyId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(policyId.hashCode(), notification)
    }
}
