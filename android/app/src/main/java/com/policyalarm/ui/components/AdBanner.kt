package com.policyalarm.ui.components

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

/**
 * @param loadWhenVisible true 면 광고 영역이 화면에 1px 이라도 들어온 뒤에 요청한다. 스크롤 아래쪽
 *   광고를 화면 진입과 동시에 요청하면 보지도 않을 광고를 받아 버려 노출률(요청 대비 노출)이 떨어진다.
 */
@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    size: AdSize = AdSize.BANNER,
    loadWhenVisible: Boolean = false,
) {
    var adView by remember { mutableStateOf<AdView?>(null) }
    var requested by remember { mutableStateOf(false) }

    // 화면이 멈추면 광고 새로고침도 멈춘다 (백그라운드·다른 화면에서 요청이 나가지 않도록).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, adView) {
        val view = adView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.resume()
                Lifecycle.Event.ON_PAUSE -> view?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier.onGloballyPositioned { coords ->
            val view = adView ?: return@onGloballyPositioned
            // boundsInWindow 는 스크롤 뷰포트 등 조상 영역으로 잘리므로, 화면 밖이면 높이가 0 이다.
            if (!requested && (!loadWhenVisible || coords.boundsInWindow().height > 0f)) {
                requested = true
                view.loadAd(AdRequest.Builder().build())
            }
        },
        factory = { context ->
            AdView(context).apply {
                setAdSize(size)
                adUnitId = BANNER_AD_UNIT_ID
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
            }.also { adView = it }
        },
        // 화면을 떠날 때 AdView 를 파괴해야 WebView·새로고침 타이머가 남지 않는다.
        onRelease = { it.destroy() },
    )
}

/**
 * 본문 사이에 넣는 300x250 인라인 광고. 하단 고정 배너보다 노출 단가가 높고, 사용자가
 * 실제로 시간을 보내는 상세·피드 스크롤 안에 놓인다. 같은 배너 광고 단위를 쓰므로
 * AdMob 콘솔에서 새 단위를 만들 필요가 없다. 오클릭을 줄이려고 위아래에 "광고" 라벨과
 * 여백을 두어 카드·버튼과 떨어뜨린다. 스크롤해서 보일 때만 요청한다.
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
            loadWhenVisible = true,
        )
    }
}
