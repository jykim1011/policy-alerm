package com.policyalarm.ui.components

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.policyalarm.BuildConfig

// 개발(debug) 중에는 Google 공식 테스트 배너 ID를 써서 실광고 오클릭(정책 위반)을 방지하고,
// 릴리스에서만 실제 광고 단위 ID로 광고를 노출한다.
private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
private const val PROD_BANNER_AD_UNIT_ID = "ca-app-pub-4710152968528474/3100150098"

private val BANNER_AD_UNIT_ID =
    if (BuildConfig.DEBUG) TEST_BANNER_AD_UNIT_ID else PROD_BANNER_AD_UNIT_ID

@Composable
fun AdBanner(modifier: Modifier = Modifier, size: AdSize = AdSize.BANNER) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                setAdSize(size)
                adUnitId = BANNER_AD_UNIT_ID
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}

/**
 * 본문 사이에 넣는 300x250 인라인 광고. 하단 고정 배너보다 노출 단가가 높고, 사용자가
 * 실제로 시간을 보내는 상세·피드 스크롤 안에 놓인다. 같은 배너 광고 단위를 쓰므로
 * AdMob 콘솔에서 새 단위를 만들 필요가 없다. 오클릭을 줄이려고 위아래에 "광고" 라벨과
 * 여백을 두어 카드·버튼과 떨어뜨린다.
 */
@Composable
fun InlineAd(modifier: Modifier = Modifier) {
    val c = com.policyalarm.ui.theme.LocalAppColors.current
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("광고", color = c.fgFaint, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        AdBanner(
            modifier = Modifier.size(width = 300.dp, height = 250.dp),
            size = AdSize.MEDIUM_RECTANGLE,
        )
    }
}
