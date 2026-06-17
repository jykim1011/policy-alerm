package com.policyalarm

import android.app.Application
import com.google.firebase.FirebaseApp
import com.policyalarm.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PolicyAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(this@PolicyAlarmApp)
                    .notificationHistoryDao()
                    .deleteOlderThan(cutoff)
            } catch (_: Exception) {}
        }
    }
}
