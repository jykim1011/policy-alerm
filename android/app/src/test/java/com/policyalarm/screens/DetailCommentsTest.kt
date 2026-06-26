package com.policyalarm.screens

import com.policyalarm.data.model.Comment
import com.policyalarm.data.repository.CommentRepository
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.data.repository.UserRepository
import com.policyalarm.ui.screens.detail.DetailViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailCommentsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val policyRepo = mockk<PolicyRepository>(relaxed = true)
    private val userRepo = mockk<UserRepository>(relaxed = true)
    private val commentRepo = mockk<CommentRepository>(relaxed = true)

    @Before fun setup() { Dispatchers.setMain(testDispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `loadComments는 평탄 리스트를 2단계 스레드로 묶는다`() = runTest {
        coEvery { commentRepo.getComments("p1") } returns listOf(
            Comment("a", "u", "닉", "부모", null, null, 100, false),
            Comment("a1", "u", "닉", "자식", "a", "닉", 150, false),
        )
        val vm = DetailViewModel(policyRepo, userRepo, commentRepo)

        vm.loadComments("p1")

        val threads = vm.uiState.value.commentThreads
        assertEquals(1, threads.size)
        assertEquals("a", threads[0].parent.id)
        assertEquals(listOf("a1"), threads[0].replies.map { it.id })
    }
}
