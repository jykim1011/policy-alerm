package com.policyalarm.ui.screens.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.local.AppDatabase
import com.policyalarm.data.local.NotificationHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HistoryViewModel(context: Context) : ViewModel() {
    private val dao = AppDatabase.getInstance(context.applicationContext).notificationHistoryDao()

    val items: Flow<List<NotificationHistoryEntity>> = dao.observeAll()

    fun markRead(policyId: String) = viewModelScope.launch { dao.markRead(policyId) }

    /** "모두 읽음": 받은 알림 목록을 전부 제거한다(내용이 모두 사라짐). */
    fun clearAll() = viewModelScope.launch { dao.deleteAll() }
}

class HistoryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HistoryViewModel(context) as T
    }
}
