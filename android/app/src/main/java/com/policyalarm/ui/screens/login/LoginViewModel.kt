package com.policyalarm.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.policyalarm.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val isNewUser: Boolean) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val userRepo: UserRepository = UserRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun signInWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val isNewUser = result.additionalUserInfo?.isNewUser == true

                val fcmToken = FirebaseMessaging.getInstance().token.await()
                if (isNewUser) {
                    userRepo.saveUserSettings(
                        fcmToken = fcmToken,
                        subscribedCategories = listOf("청약", "대출", "세금"),
                        notificationSchedule = "both",
                    )
                } else {
                    userRepo.updateFcmToken(fcmToken)
                }

                _uiState.value = LoginUiState.Success(isNewUser)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "로그인 실패")
            }
        }
    }

    /** Handle a Google Sign-In failure surfaced from the activity result. */
    fun onSignInError(statusCode: Int) {
        _uiState.value = when (statusCode) {
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED ->
                // User backed out of the account picker; not an error worth showing.
                LoginUiState.Idle
            CommonStatusCodes.DEVELOPER_ERROR -> LoginUiState.Error(
                "로그인 설정 오류 (코드 10): Firebase에 앱의 SHA-1 지문과 " +
                    "Google 로그인 설정이 등록되어 있는지 확인하세요."
            )
            CommonStatusCodes.NETWORK_ERROR ->
                LoginUiState.Error("네트워크 오류로 로그인에 실패했습니다.")
            else ->
                LoginUiState.Error("로그인 실패 (코드 $statusCode)")
        }
    }
}
