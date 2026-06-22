package com.policyalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.policyalarm.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PolicyAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        createNotificationChannel()
        MobileAds.initialize(this) {}
        syncFcmTokenOnAuthReady()
    }

    // 백그라운드/종료 상태에서 도착하는 FCM notification 페이로드는 OS가 직접 표시하며,
    // manifest 의 default_notification_channel_id(policy_alerts_v2) 채널이 그 시점에
    // 이미 존재해야 배너가 뜬다. 기존엔 포그라운드 수신(PolicyFcmService) 때만 채널을
    // 만들어, 첫 푸시를 백그라운드로 받으면 채널이 없어 배너가 누락됐다(앱 시작 시 생성).
    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            FCM_CHANNEL_ID, "정책 알림", NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val FCM_CHANNEL_ID = "policy_alerts_v2"
    }

    // onNewToken이 Firebase Auth 세션 복원 전에 불리면 토큰 업데이트가 실패하므로
    // Auth 상태가 확정된 뒤 한 번 더 현재 토큰을 Firestore에 동기화한다.
    private fun syncFcmTokenOnAuthReady() {
        val auth = FirebaseAuth.getInstance()
        val listener = object : FirebaseAuth.AuthStateListener {
            override fun onAuthStateChanged(fa: FirebaseAuth) {
                fa.currentUser ?: return
                auth.removeAuthStateListener(this)
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        val token = FirebaseMessaging.getInstance().token.await()
                        UserRepository().updateFcmToken(token)
                    }
                }
            }
        }
        auth.addAuthStateListener(listener)
    }
}
