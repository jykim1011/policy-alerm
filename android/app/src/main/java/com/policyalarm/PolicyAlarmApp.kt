package com.policyalarm

import android.app.Application
import com.google.firebase.FirebaseApp

class PolicyAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
