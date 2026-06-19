package com.policyalarm.ui.screens.licenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.policyalarm.ui.theme.LocalAppColors

private data class OssLibrary(val name: String, val license: String)

// 앱이 사용하는 주요 오픈소스 라이브러리. 모두 Apache License 2.0이다.
private val OSS_LIBRARIES = listOf(
    OssLibrary("AndroidX (Core, Lifecycle, Activity, Navigation)", "Apache License 2.0"),
    OssLibrary("Jetpack Compose", "Apache License 2.0"),
    OssLibrary("Material Components for Android (Material 3)", "Apache License 2.0"),
    OssLibrary("AndroidX Room", "Apache License 2.0"),
    OssLibrary("Kotlin", "Apache License 2.0"),
    OssLibrary("Kotlin Coroutines", "Apache License 2.0"),
    OssLibrary("Retrofit", "Apache License 2.0"),
    OssLibrary("OkHttp", "Apache License 2.0"),
    OssLibrary("Gson", "Apache License 2.0"),
    OssLibrary("Firebase Android SDK", "Apache License 2.0"),
    OssLibrary("Google Play Services (Auth, Ads)", "Apache License 2.0"),
)

private const val APACHE_2_0_NOTICE =
    "이 앱은 아래 오픈소스 라이브러리를 사용합니다. 각 라이브러리는 Apache License 2.0 " +
        "조건에 따라 배포됩니다.\n\n" +
        "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use these " +
        "files except in compliance with the License. You may obtain a copy of the License at " +
        "http://www.apache.org/licenses/LICENSE-2.0\n\n" +
        "Unless required by applicable law or agreed to in writing, software distributed under " +
        "the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF " +
        "ANY KIND, either express or implied."

@Composable
fun OssLicensesScreen(onBack: () -> Unit) {
    val c = LocalAppColors.current

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgApp),
    ) {
        // top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bgSurface)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = c.fgStrong,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("오픈소스 라이선스", color = c.fgStrong, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            item {
                Text(
                    APACHE_2_0_NOTICE,
                    color = c.fgMuted,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(18.dp))
            }
            items(OSS_LIBRARIES) { lib ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.bgSurface)
                        .border(1.dp, c.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Text(lib.name, color = c.fgStrong, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(lib.license, color = c.fgSubtle, fontSize = 12.5.sp)
                }
            }
        }
    }
}
