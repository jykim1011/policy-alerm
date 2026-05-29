package com.policyalarm.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
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
}
