package com.policyalarm.ui.screens.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.policyalarm.data.local.AppDatabase
import com.policyalarm.data.remote.RetrofitClient
import com.policyalarm.data.repository.PolicyRepository

class DetailViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getInstance(context)
        val repo = PolicyRepository(RetrofitClient.policyApi, db.readPolicyDao())
        @Suppress("UNCHECKED_CAST")
        return DetailViewModel(repo) as T
    }
}
