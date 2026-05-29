package com.policyalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.policyalarm.ui.navigation.AppNavigation
import com.policyalarm.ui.theme.PolicyAlarmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val policyId = intent.getStringExtra("policy_id")
        setContent {
            PolicyAlarmTheme {
                AppNavigation(startPolicyId = policyId)
            }
        }
    }
}
