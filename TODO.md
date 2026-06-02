# 배포 전 남은 작업 (TODO)

## 남은 작업

### 1. Firebase 서비스 계정 JSON 다운로드
- Firebase Console → https://console.firebase.google.com/project/policy-alerm/settings/serviceaccounts/adminsdk
- "새 비공개 키 생성" → JSON 파일 다운로드

### 2. GitHub Secrets 등록 (2개)
명령어 (서비스 계정 JSON 파일 경로 대입):
```bash
# FIREBASE_SERVICE_ACCOUNT
gh secret set FIREBASE_SERVICE_ACCOUNT --body "$(cat C:\path\to\service-account.json)" --repo jykim1011/policy-alerm

# ANTHROPIC_API_KEY (console.anthropic.com에서 발급)
gh secret set ANTHROPIC_API_KEY --body "sk-ant-..." --repo jykim1011/policy-alerm
```
확인: https://github.com/jykim1011/policy-alerm/settings/secrets/actions

### 3. Firebase 설정 (Console에서 직접)
- Firestore Database 활성화: https://console.firebase.google.com/project/policy-alerm/firestore
  - 시작 모드: 테스트 모드 (30일 후 규칙 수정 필요)
- Authentication 활성화: https://console.firebase.google.com/project/policy-alerm/authentication
  - 로그인 방법 → Google 사용 설정

### 4. Firebase Cloud Function 배포
```bash
cd D:\policy-alerm\functions
npm install
firebase use policy-alerm
firebase deploy --only functions
```
주의: Cloud Functions는 Blaze(종량제) 요금제 필요

### 5. GitHub Actions 첫 실행 테스트
- https://github.com/jykim1011/policy-alerm/actions
- "Policy Pipeline" → "Run workflow" → batch: morning → Run
- 성공 시 docs/policies/index.json 파일이 커밋됨
- GitHub Pages URL 확인: https://jykim1011.github.io/policy-alerm/policies/index.json

### 6. Android 앱 빌드
- Android Studio에서 android/ 폴더 열기
- google-services.json은 android/app/에 이미 있음
- Build → Make Project
- 에뮬레이터 또는 실기기에서 실행

---

## 7. Google Play Console 등록 및 출시 절차

앱 정보: `applicationId = com.policyalarm` · `versionCode = 1` · `versionName = 1.0`
(버전은 `android/app/build.gradle.kts`에서 관리)

### 7-1. 개발자 계정 등록
- https://play.google.com/console 접속 → Google 계정으로 가입
- **등록비 $25 (1회성)** 결제, 신원 확인(개인/사업자) 완료까지 최대 며칠 소요
- 개인 개발자는 2023년 11월 이후 가입 시 **출시 전 비공개 테스트(테스터 12명, 14일 이상)** 요건이 있을 수 있음 → 7-7 참고

### 7-2. 릴리스 서명 키스토어 생성 (최초 1회)
릴리스 빌드는 디버그 키가 아닌 **업로드 키**로 서명해야 한다.
```bash
keytool -genkeypair -v -keystore D:\keys\policy-alarm-upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias policyalarm
```
- 생성된 `.jks`와 비밀번호는 **분실 금지·git 커밋 금지**(`.gitignore`의 `D:\keys`는 저장소 밖이라 안전)

### 7-3. 서명 설정 (`android/app/build.gradle.kts`)
키 정보는 코드에 넣지 말고 `android/keystore.properties`(git 무시)로 분리:
```properties
# android/keystore.properties  (커밋하지 말 것)
storeFile=D:/keys/policy-alarm-upload.jks
storePassword=********
keyAlias=policyalarm
keyPassword=********
```
`build.gradle.kts`에 추가:
```kotlin
import java.util.Properties

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false   // R8 적용 시 true + proguard 규칙 검토
            // ...
        }
    }
}
```
`android/.gitignore`(또는 루트)에 `keystore.properties` 추가.

### 7-4. 릴리스 AAB 빌드
Play Console은 APK가 아닌 **AAB(App Bundle)** 업로드를 요구:
```bash
cd D:\policy-alerm\android
./gradlew bundleRelease
# 산출물: app/build/outputs/bundle/release/app-release.aab
```

### 7-5. 앱 만들기 + 스토어 등록정보
Play Console → "앱 만들기" → 앱 이름(정책 알리미), 언어(한국어), 앱/무료 여부 선택. 이후:
- **스토어 등록정보**: 짧은 설명(80자), 자세한 설명, 앱 아이콘 **512×512 PNG**, 그래픽 이미지(피처 그래픽) **1024×500**, 휴대전화 스크린샷 최소 2장(권장 4~8장)
- **카테고리**: 뉴스/잡지 또는 금융

### 7-6. 정책 관련 필수 입력 (앱 콘텐츠)
- **개인정보처리방침 URL** 필수 (로그인·FCM 사용) — 예: GitHub Pages에 `docs/privacy.html` 추가 후 그 URL 사용
- **데이터 보안(Data safety)**: 수집 항목 신고 → 본 앱은 Google 로그인(이메일/계정 ID), FCM 토큰 사용
- **콘텐츠 등급** 설문 작성
- **타겟 연령층** 선택
- **광고 포함 여부**: AdMob 적용 시 **"예, 광고 포함"으로 신고** (→ 8번 가이드)
- **앱 액세스**: 로그인이 필요하므로 심사용 테스트 계정 또는 안내 제공

### 7-7. 테스트 → 프로덕션 출시
- **Play 앱 서명** 사용(권장): 업로드 키로 서명해 올리면 Google이 최종 서명 키를 관리
- 트랙 순서: 내부 테스트 → 비공개(Closed) 테스트 → 공개(Open) → **프로덕션**
- 신규 개인 계정은 비공개 테스트(테스터 12명+, 14일 이상) 통과 후 프로덕션 신청 가능
- 새 릴리스마다 `versionCode`를 1씩 올려야 함(`build.gradle.kts`)

---

## 8. AdMob 광고 추가 가이드

> 먼저 **테스트 광고 단위 ID**로 통합·검증한 뒤, 출시 직전에 실제 광고 단위 ID로 교체할 것.
> (개발 중 실제 광고를 클릭/노출하면 계정 정지 위험)

### 8-1. AdMob 계정·앱·광고 단위 생성
- https://admob.google.com 가입 → "앱 추가" → 플랫폼 Android, 패키지명 `com.policyalarm`
- 발급 항목:
  - **앱 ID**: `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY` (~ 포함)
  - **광고 단위 ID**(배너 등): `ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ` (/ 포함)
- 테스트용 공개 ID(개발 중 사용):
  - 앱 ID(테스트): `ca-app-pub-3940256099942544~3347511713`
  - 배너(테스트): `ca-app-pub-3940256099942544/6300978111`

### 8-2. SDK 의존성 추가
`android/gradle/libs.versions.toml`:
```toml
[versions]
playServicesAds = "23.6.0"   # 출시 전 AdMob 문서에서 최신 버전 확인

[libraries]
play-services-ads = { group = "com.google.android.gms", name = "play-services-ads", version.ref = "playServicesAds" }
```
`android/app/build.gradle.kts` 의 dependencies:
```kotlin
implementation(libs.play.services.ads)
```

### 8-3. AndroidManifest에 앱 ID 등록
`android/app/src/main/AndroidManifest.xml` 의 `<application>` 안에 추가:
```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-3940256099942544~3347511713"/> <!-- 출시 시 실제 앱 ID로 교체 -->
```
> INTERNET 권한은 이미 매니페스트에 있음. 앱 ID 누락 시 앱이 시작 시 크래시하므로 반드시 등록.

### 8-4. SDK 초기화 (`PolicyAlarmApp.kt`)
```kotlin
import com.google.android.gms.ads.MobileAds

class PolicyAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        MobileAds.initialize(this) {}
    }
}
```

### 8-5. Compose 배너 컴포저블
이 앱은 Jetpack Compose이므로 `AndroidView`로 `AdView`를 감싼다. 예: `ui/components/AdBanner.kt`
```kotlin
package com.policyalarm.ui.components

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111" // 출시 시 실제 ID로 교체

@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
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
```
사용 예 — 홈 화면 하단 등에 배치:
```kotlin
// HomeScreen.kt 의 Column 하단 또는 MainScaffold 의 bottomBar 위
AdBanner(Modifier.fillMaxWidth())
```
> 실제 광고 단위 ID는 `BuildConfig` 필드나 별도 상수로 분리해 테스트/프로덕션을 구분하는 것을 권장.

### 8-6. 출시 전 체크리스트
- [ ] 매니페스트 앱 ID·코드의 광고 단위 ID를 **실제 ID로 교체**
- [ ] AdMob 콘솔에서 **app-ads.txt** 게시(웹사이트 보유 시) 권장
- [ ] **사용자 동의(UMP/GDPR·개인정보 보호)** SDK 적용 — 유럽/한국 개인정보 동의 처리 필요 시 `com.google.android.ump:user-messaging-platform` 추가
- [ ] Play Console "광고 포함" 신고(7-6)
- [ ] 테스트 기기 등록 후 테스트 광고만 노출되는지 확인

---

## 프로젝트 구조 요약
```
D:\policy-alerm\
  pipeline/          ← Python 크롤링/요약/배포 파이프라인
  functions/         ← Firebase Cloud Function (FCM 발송)
  docs/              ← GitHub Pages CDN (정책 JSON 저장 위치)
  android/           ← Android 앱 (Kotlin + Jetpack Compose)
  .github/workflows/ ← GitHub Actions (매일 9시/18시 KST 자동 실행)
```

## 핵심 URL
- GitHub: https://github.com/jykim1011/policy-alerm
- CDN (Pages): https://jykim1011.github.io/policy-alerm/
- Firebase: https://console.firebase.google.com/project/policy-alerm
