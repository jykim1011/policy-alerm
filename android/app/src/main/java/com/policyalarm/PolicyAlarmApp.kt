package com.policyalarm

import android.app.Application
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
        MobileAds.initialize(this) {}
        syncFcmTokenOnAuthReady()
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
