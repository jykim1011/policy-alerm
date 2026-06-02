package com.policyalarm.ui.screens.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.policyalarm.R
import com.policyalarm.ui.components.Emoji
import com.policyalarm.ui.components.GhostButton
import com.policyalarm.ui.components.PolicyAppIcon
import com.policyalarm.ui.theme.LocalAppColors

@Composable
private fun GoogleGlyph() {
    // Simple "G" mark in Google blue (full 4-color logo omitted for native build).
    Text("G", color = Color(0xFF4285F4), fontSize = 19.sp, fontWeight = FontWeight.Bold)
}

private data class ValueProp(val emoji: String, val title: String, val desc: String)

private val VALUE_PROPS = listOf(
    ValueProp("🔔", "발표 즉시 푸시 알림", "국토부·기재부 등 정책을 자동 수집"),
    ValueProp("🤖", "AI 3줄 요약", "무엇이·누가·언제부터 한눈에"),
    ValueProp("🔑", "관심 분야만 골라보기", "청약·대출·세금·재개발·전월세"),
)

@Composable
fun LoginScreen(
    onLoginSuccess: (isNewUser: Boolean) -> Unit,
    vm: LoginViewModel = viewModel(),
) {
    val context = LocalContext.current
    val c = LocalAppColors.current
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
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                vm.signInWithGoogle(task.getResult(ApiException::class.java))
            } catch (e: ApiException) {
                vm.onSignInError(e.statusCode)
            }
        } else {
            try {
                task.getResult(ApiException::class.java)
            } catch (e: ApiException) {
                vm.onSignInError(e.statusCode)
            }
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
            .background(c.bgApp)
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PolicyAppIcon(size = 84, corner = 23)
            Spacer(Modifier.height(16.dp))
            Text(
                "정책 알리미",
                color = c.fgStrong,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "흩어진 부동산 정책 발표를\n한 곳에서, 알림으로 받아보세요",
                color = c.fgMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(30.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                VALUE_PROPS.forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.govTint),
                            contentAlignment = Alignment.Center,
                        ) { Emoji(v.emoji, 22) }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(v.title, color = c.fgStrong, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                            Text(v.desc, color = c.fgSubtle, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }

        if (uiState is LoginUiState.Loading) {
            Box(Modifier.fillMaxWidth().padding(bottom = 22.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
        } else {
            Column {
                GhostButton(onClick = { launcher.launch(googleSignInClient.signInIntent) }, height = 54) {
                    GoogleGlyph()
                    Spacer(Modifier.width(10.dp))
                    Text("Google로 계속하기", color = c.fgDefault, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                }
                if (uiState is LoginUiState.Error) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        (uiState as LoginUiState.Error).message,
                        color = c.danger,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "계속 진행하면 이용약관 및 개인정보처리방침에 동의하게 됩니다.",
                    color = c.fgFaint,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 22.dp),
                )
            }
        }
    }
}
