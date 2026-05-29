package com.policyalarm.ui.screens.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.policyalarm.R

@Composable
fun LoginScreen(
    onLoginSuccess: (isNewUser: Boolean) -> Unit,
    vm: LoginViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            task.result?.let { vm.signInWithGoogle(it) }
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess((uiState as LoginUiState.Success).isNewUser)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("정책 알림", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("부동산 정책을 가장 빠르게 받아보세요", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(48.dp))

        if (uiState is LoginUiState.Loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { launcher.launch(googleSignInClient.signInIntent) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Google로 시작하기")
            }
        }

        if (uiState is LoginUiState.Error) {
            Spacer(Modifier.height(16.dp))
            Text(
                (uiState as LoginUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
