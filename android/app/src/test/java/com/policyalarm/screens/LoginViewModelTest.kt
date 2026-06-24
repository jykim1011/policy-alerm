package com.policyalarm.screens

import com.policyalarm.data.repository.UserRepository
import com.policyalarm.ui.screens.login.LoginViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val userRepo = mockk<UserRepository>(relaxed = true)

    @Test
    fun `신규 가입이면 닉네임을 보장한다`() = runTest {
        coEvery { userRepo.ensureNickname() } returns "용감한수달1234"
        val vm = LoginViewModel(mockk(relaxed = true), userRepo)

        vm.ensureProfileForNewUser(isNewUser = true)

        coVerify(exactly = 1) { userRepo.ensureNickname() }
    }

    @Test
    fun `기존 사용자면 닉네임 생성을 강제하지 않는다`() = runTest {
        val vm = LoginViewModel(mockk(relaxed = true), userRepo)

        vm.ensureProfileForNewUser(isNewUser = false)

        coVerify(exactly = 0) { userRepo.ensureNickname() }
    }
}
