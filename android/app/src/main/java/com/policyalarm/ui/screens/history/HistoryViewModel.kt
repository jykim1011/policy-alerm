package com.policyalarm.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.repository.NotificationItem
import com.policyalarm.data.repository.NotificationRepository
import com.policyalarm.data.repository.PolicyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val policyRepo: PolicyRepository,
    private val repo: NotificationRepository = NotificationRepository(),
) : ViewModel() {

    // 읽은 알림은 리스트에서 숨긴다(알림 문서 자체는 30일 보관 정책에 따라 유지).
    val items: Flow<List<NotificationItem>> = repo.observeNotifications()
        .map { list -> list.filter { !it.isRead } }

    fun markRead(policyId: String) = viewModelScope.launch { repo.markRead(policyId) }

    /**
     * "모두 읽음": 받은 알림 목록을 전부 제거하고, 해당 정책들을 로컬 읽음 기록에도 남긴다.
     * 홈의 "안 읽은 정책 N건" 수치는 로컬 읽음 기록을 기준으로 하므로 함께 갱신돼야 한다.
     */
    fun clearAll() = viewModelScope.launch {
        val cleared = repo.clearAll()
        runCatching { policyRepo.markAllAsRead(cleared) }
    }
}
