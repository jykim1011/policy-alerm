package com.policyalarm.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.repository.NotificationItem
import com.policyalarm.data.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repo: NotificationRepository = NotificationRepository(),
) : ViewModel() {

    val items: Flow<List<NotificationItem>> = repo.observeNotifications()

    fun markRead(policyId: String) = viewModelScope.launch { repo.markRead(policyId) }

    /** "모두 읽음": 받은 알림 목록을 전부 제거한다(내용이 모두 사라짐). */
    fun clearAll() = viewModelScope.launch { repo.clearAll() }
}
