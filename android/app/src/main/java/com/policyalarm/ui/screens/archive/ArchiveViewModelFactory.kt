package com.policyalarm.ui.screens.archive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.policyalarm.data.local.AppDatabase
import com.policyalarm.data.remote.RetrofitClient
import com.policyalarm.data.repository.PolicyRepository

class ArchiveViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(ArchiveViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        val db = AppDatabase.getInstance(appContext)
        val repo = PolicyRepository(RetrofitClient.policyApi, db.readPolicyDao())
        @Suppress("UNCHECKED_CAST")
        return ArchiveViewModel(repo) as T
    }
}
