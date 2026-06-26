package com.policyalarm.screens

import com.policyalarm.data.repository.UserRepository
import com.policyalarm.ui.screens.settings.SettingsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
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
class SettingsNicknameTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val userRepo = mockk<UserRepository>(relaxed = true)

    @Before fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { userRepo.getUserSettings() } returns emptyMap()
        coEvery { userRepo.ensureNickname() } returns "용감한수달1234"
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `초기 로드 시 닉네임을 채운다`() = runTest {
        val vm = SettingsViewModel(userRepo, mockk(relaxed = true))
        assertEquals("용감한수달1234", vm.uiState.value.nickname)
    }

    @Test
    fun `setNickname은 상태를 갱신하고 저장한다`() = runTest {
        val vm = SettingsViewModel(userRepo, mockk(relaxed = true))
        vm.setNickname("나의새닉")
        assertEquals("나의새닉", vm.uiState.value.nickname)
        coVerify { userRepo.updateNickname("나의새닉") }
    }
}
