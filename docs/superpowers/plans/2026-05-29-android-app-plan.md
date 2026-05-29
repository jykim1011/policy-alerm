# Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 부동산 정책 알림 Android 앱 구현 — Google 로그인, 정책 피드(CDN fetch), 구조화 요약 상세 화면, FCM 푸시 알림 수신, 카테고리 구독 설정

**Architecture:** 단일 Activity + Jetpack Compose, MVVM + Repository 패턴. 정책 데이터는 GitHub Pages CDN에서 Retrofit으로 fetch하고, 읽은 기록은 Room에 로컬 저장한다. Firebase Auth(Google), Firestore(유저 설정), FCM(푸시)을 사용한다.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Retrofit2 + Gson, Room, Firebase Auth/Firestore/FCM, Google Sign-In, Coroutines + Flow

**CDN Base URL:** `https://{GITHUB_USERNAME}.github.io/policy-alarm/` (실제 값으로 교체 필요)

**⚠️ 선행 조건:**
- Android Studio 최신 버전 설치
- `google-services.json` 다운로드 (Firebase Console → 프로젝트 설정 → Android 앱 추가)
- Pipeline Plan 완료 후 GitHub Pages CDN URL 확인

---

## 파일 구조

```
app/
  build.gradle.kts
  google-services.json                    # Firebase 설정 (gitignore에 추가 금지)
  src/main/
    AndroidManifest.xml
    java/com/policyalarm/
      PolicyAlarmApp.kt                   # Application, Firebase 초기화
      MainActivity.kt                     # 단일 Activity
      
      data/
        model/
          PolicyItem.kt                   # CDN index.json 아이템
          PolicyDetail.kt                 # CDN {id}.json 전체 (summary 포함)
        local/
          AppDatabase.kt                  # Room DB 정의
          ReadPolicyEntity.kt             # 읽은 정책 ID 저장
          NotificationHistoryEntity.kt    # 알림 수신 기록
          ReadPolicyDao.kt
          NotificationHistoryDao.kt
        remote/
          PolicyApiService.kt             # Retrofit 인터페이스
          RetrofitClient.kt               # Retrofit 싱글톤
        repository/
          PolicyRepository.kt             # CDN fetch + 읽음 상태 조합
          UserRepository.kt              # Firestore 유저 설정 + FCM 토큰
      
      service/
        PolicyFcmService.kt              # FirebaseMessagingService 서브클래스
      
      ui/
        theme/
          Color.kt
          Theme.kt
          Type.kt
        navigation/
          AppNavigation.kt               # NavHost + route 상수
        screens/
          splash/SplashScreen.kt
          login/
            LoginScreen.kt
            LoginViewModel.kt
          onboarding/
            OnboardingScreen.kt
            OnboardingViewModel.kt
          home/
            HomeScreen.kt
            HomeViewModel.kt
          detail/
            DetailScreen.kt
            DetailViewModel.kt
          history/
            HistoryScreen.kt
            HistoryViewModel.kt
          settings/
            SettingsScreen.kt
            SettingsViewModel.kt
  
  src/test/java/com/policyalarm/
    repository/PolicyRepositoryTest.kt
    repository/UserRepositoryTest.kt
    screens/HomeViewModelTest.kt
    screens/DetailViewModelTest.kt
```

---

### Task 1: 프로젝트 설정 및 의존성

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `build.gradle.kts` (프로젝트 루트)

- [ ] **Step 1: Android Studio에서 새 프로젝트 생성**

`File → New Project → Empty Activity`
- Name: `PolicyAlarm`
- Package: `com.policyalarm`
- Language: Kotlin
- Min SDK: API 26 (Android 8.0)

- [ ] **Step 2: 루트 build.gradle.kts 플러그인 추가**

```kotlin
// build.gradle.kts (프로젝트 루트)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
}
```

- [ ] **Step 3: app/build.gradle.kts 의존성 추가**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.policyalarm"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.policyalarm"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "CDN_BASE_URL",
            "\"https://YOUR_GITHUB_USERNAME.github.io/policy-alarm/\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity + Lifecycle
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.12")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
```

- [ ] **Step 4: google-services.json 배치**

Firebase Console → 프로젝트 설정 → Android 앱 → `google-services.json` 다운로드 → `app/` 폴더에 저장

- [ ] **Step 5: 빌드 확인**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add app/build.gradle.kts build.gradle.kts app/google-services.json
git commit -m "feat: Android project setup with all dependencies"
```

---

### Task 2: 데이터 모델

**Files:**
- Create: `app/src/main/java/com/policyalarm/data/model/PolicyItem.kt`
- Create: `app/src/main/java/com/policyalarm/data/model/PolicyDetail.kt`

- [ ] **Step 1: PolicyItem.kt 작성**

```kotlin
package com.policyalarm.data.model

import com.google.gson.annotations.SerializedName

data class PolicyItem(
    val id: String,
    val category: String,
    val subcategory: String,
    val title: String,
    val source: String,
    @SerializedName("published_at") val publishedAt: String,
    @SerializedName("summary_preview") val summaryPreview: String,
)

data class PolicyIndex(
    @SerializedName("updated_at") val updatedAt: String,
    val total: Int,
    val items: List<PolicyItem>,
)
```

- [ ] **Step 2: PolicyDetail.kt 작성**

```kotlin
package com.policyalarm.data.model

import com.google.gson.annotations.SerializedName

data class PolicySummary(
    @SerializedName("what_changed") val whatChanged: String,
    @SerializedName("who_is_affected") val whoIsAffected: String,
    @SerializedName("when_effective") val whenEffective: String,
    @SerializedName("key_points") val keyPoints: List<String>,
)

data class PolicyDetail(
    val id: String,
    val category: String,
    val subcategory: String,
    val title: String,
    val source: String,
    @SerializedName("source_url") val sourceUrl: String,
    @SerializedName("file_url") val fileUrl: String?,
    @SerializedName("file_type") val fileType: String?,
    @SerializedName("published_at") val publishedAt: String,
    @SerializedName("crawled_at") val crawledAt: String,
    val summary: PolicySummary?,
)
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/policyalarm/data/model/
git commit -m "feat: data models matching CDN JSON schema"
```

---

### Task 3: Room 데이터베이스

**Files:**
- Create: `app/src/main/java/com/policyalarm/data/local/ReadPolicyEntity.kt`
- Create: `app/src/main/java/com/policyalarm/data/local/NotificationHistoryEntity.kt`
- Create: `app/src/main/java/com/policyalarm/data/local/ReadPolicyDao.kt`
- Create: `app/src/main/java/com/policyalarm/data/local/NotificationHistoryDao.kt`
- Create: `app/src/main/java/com/policyalarm/data/local/AppDatabase.kt`

- [ ] **Step 1: Entity 클래스 작성**

`ReadPolicyEntity.kt`:
```kotlin
package com.policyalarm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_policies")
data class ReadPolicyEntity(
    @PrimaryKey val policyId: String,
    val readAt: Long = System.currentTimeMillis(),
)
```

`NotificationHistoryEntity.kt`:
```kotlin
package com.policyalarm.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_history")
data class NotificationHistoryEntity(
    @PrimaryKey val policyId: String,
    val title: String,
    val category: String,
    val receivedAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 2: DAO 작성**

`ReadPolicyDao.kt`:
```kotlin
package com.policyalarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadPolicyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markAsRead(entity: ReadPolicyEntity)

    @Query("SELECT policyId FROM read_policies")
    fun observeReadIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) > 0 FROM read_policies WHERE policyId = :policyId")
    suspend fun isRead(policyId: String): Boolean
}
```

`NotificationHistoryDao.kt`:
```kotlin
package com.policyalarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NotificationHistoryEntity)

    @Query("SELECT * FROM notification_history ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<NotificationHistoryEntity>>
}
```

- [ ] **Step 3: AppDatabase.kt 작성**

```kotlin
package com.policyalarm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ReadPolicyEntity::class, NotificationHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readPolicyDao(): ReadPolicyDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "policy_alarm.db"
                ).build().also { instance = it }
            }
    }
}
```

- [ ] **Step 4: 빌드 확인**

```bash
./gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/policyalarm/data/local/
git commit -m "feat: Room database with read history and notification history"
```

---

### Task 4: Retrofit 네트워크 레이어

**Files:**
- Create: `app/src/main/java/com/policyalarm/data/remote/PolicyApiService.kt`
- Create: `app/src/main/java/com/policyalarm/data/remote/RetrofitClient.kt`

- [ ] **Step 1: PolicyApiService.kt 작성**

```kotlin
package com.policyalarm.data.remote

import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.model.PolicyIndex
import retrofit2.http.GET
import retrofit2.http.Path

interface PolicyApiService {
    @GET("policies/index.json")
    suspend fun getPolicyIndex(): PolicyIndex

    @GET("policies/{id}.json")
    suspend fun getPolicyDetail(@Path("id") id: String): PolicyDetail

    @GET("categories/{subcategory}/index.json")
    suspend fun getCategoryIndex(@Path("subcategory") subcategory: String): PolicyIndex
}
```

- [ ] **Step 2: RetrofitClient.kt 작성**

```kotlin
package com.policyalarm.data.remote

import com.policyalarm.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    val policyApi: PolicyApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.CDN_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PolicyApiService::class.java)
    }
}
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/policyalarm/data/remote/
git commit -m "feat: Retrofit client for CDN API"
```

---

### Task 5: Repository 레이어

**Files:**
- Create: `app/src/main/java/com/policyalarm/data/repository/PolicyRepository.kt`
- Create: `app/src/main/java/com/policyalarm/data/repository/UserRepository.kt`
- Create: `app/src/test/java/com/policyalarm/repository/PolicyRepositoryTest.kt`

- [ ] **Step 1: 테스트 작성**

`PolicyRepositoryTest.kt`:
```kotlin
package com.policyalarm.repository

import com.policyalarm.data.local.ReadPolicyDao
import com.policyalarm.data.local.ReadPolicyEntity
import com.policyalarm.data.model.PolicyIndex
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.remote.PolicyApiService
import com.policyalarm.data.repository.PolicyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyRepositoryTest {

    private val mockApi = mockk<PolicyApiService>()
    private val mockDao = mockk<ReadPolicyDao>(relaxed = true)
    private val repo = PolicyRepository(mockApi, mockDao)

    private fun makeItem(id: String) = PolicyItem(
        id = id, category = "부동산", subcategory = "청약",
        title = "테스트 정책", source = "국토교통부",
        publishedAt = "2026-05-29T09:00:00+09:00",
        summaryPreview = "요약..."
    )

    @Test
    fun `getPolicyIndex returns items from API`() = runTest {
        val index = PolicyIndex("2026-05-29", 1, listOf(makeItem("id-001")))
        coEvery { mockApi.getPolicyIndex() } returns index

        val result = repo.getPolicyIndex()
        assertEquals(1, result.items.size)
        assertEquals("id-001", result.items[0].id)
    }

    @Test
    fun `markAsRead calls dao`() = runTest {
        repo.markAsRead("id-001")
        coVerify { mockDao.markAsRead(ReadPolicyEntity("id-001")) }
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew testDebugUnitTest --tests "com.policyalarm.repository.PolicyRepositoryTest"
```

Expected: FAILED (클래스 없음)

- [ ] **Step 3: PolicyRepository.kt 작성**

```kotlin
package com.policyalarm.data.repository

import com.policyalarm.data.local.ReadPolicyDao
import com.policyalarm.data.local.ReadPolicyEntity
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.model.PolicyIndex
import com.policyalarm.data.remote.PolicyApiService
import kotlinx.coroutines.flow.Flow

class PolicyRepository(
    private val api: PolicyApiService,
    private val readPolicyDao: ReadPolicyDao,
) {
    suspend fun getPolicyIndex(): PolicyIndex = api.getPolicyIndex()

    suspend fun getCategoryIndex(subcategory: String): PolicyIndex =
        api.getCategoryIndex(subcategory)

    suspend fun getPolicyDetail(id: String): PolicyDetail = api.getPolicyDetail(id)

    fun observeReadIds(): Flow<List<String>> = readPolicyDao.observeReadIds()

    suspend fun markAsRead(policyId: String) {
        readPolicyDao.markAsRead(ReadPolicyEntity(policyId))
    }
}
```

- [ ] **Step 4: UserRepository.kt 작성**

```kotlin
package com.policyalarm.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val uid get() = auth.currentUser?.uid ?: error("로그인 필요")

    suspend fun saveUserSettings(
        fcmToken: String,
        subscribedCategories: List<String>,
        notificationSchedule: String,
    ) {
        db.collection("users").document(uid).set(
            mapOf(
                "fcm_token" to fcmToken,
                "subscribed_categories" to subscribedCategories,
                "notification_schedule" to notificationSchedule,
            )
        ).await()
    }

    suspend fun getUserSettings(): Map<String, Any>? =
        db.collection("users").document(uid).get().await().data

    suspend fun updateFcmToken(token: String) {
        db.collection("users").document(uid)
            .update("fcm_token", token).await()
    }

    suspend fun updateSubscribedCategories(categories: List<String>) {
        db.collection("users").document(uid)
            .update("subscribed_categories", categories).await()
    }

    suspend fun updateNotificationSchedule(schedule: String) {
        db.collection("users").document(uid)
            .update("notification_schedule", schedule).await()
    }

    suspend fun saveBookmark(policyId: String) {
        db.collection("users").document(uid)
            .collection("bookmarks").document(policyId)
            .set(mapOf("bookmarked_at" to System.currentTimeMillis())).await()
    }

    suspend fun removeBookmark(policyId: String) {
        db.collection("users").document(uid)
            .collection("bookmarks").document(policyId)
            .delete().await()
    }

    suspend fun isBookmarked(policyId: String): Boolean =
        db.collection("users").document(uid)
            .collection("bookmarks").document(policyId)
            .get().await().exists()

    fun isLoggedIn(): Boolean = auth.currentUser != null
}
```

- [ ] **Step 5: 테스트 재실행 → 통과 확인**

```bash
./gradlew testDebugUnitTest --tests "com.policyalarm.repository.PolicyRepositoryTest"
```

Expected: PASSED

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/policyalarm/data/repository/ app/src/test/
git commit -m "feat: PolicyRepository and UserRepository"
```

---

### Task 6: FCM 서비스 + Application 클래스

**Files:**
- Create: `app/src/main/java/com/policyalarm/service/PolicyFcmService.kt`
- Create: `app/src/main/java/com/policyalarm/PolicyAlarmApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: PolicyFcmService.kt 작성**

```kotlin
package com.policyalarm.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.policyalarm.MainActivity
import com.policyalarm.R
import com.policyalarm.data.local.AppDatabase
import com.policyalarm.data.local.NotificationHistoryEntity
import com.policyalarm.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PolicyFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val policyId = message.data["policy_id"] ?: return
        val title = message.notification?.title ?: "새 정책"
        val body = message.notification?.body ?: ""
        val category = message.data["category"] ?: ""

        // 알림 히스토리 저장
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getInstance(applicationContext)
                .notificationHistoryDao()
                .insert(NotificationHistoryEntity(policyId, body, category))
        }

        showNotification(policyId, title, body)
    }

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                UserRepository().updateFcmToken(token)
            } catch (_: Exception) {
                // 로그인 전이면 무시 — 로그인 시 토큰 등록
            }
        }
    }

    private fun showNotification(policyId: String, title: String, body: String) {
        val channelId = "policy_alerts"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(channelId, "정책 알림", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("policy_id", policyId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(policyId.hashCode(), notification)
    }
}
```

- [ ] **Step 2: PolicyAlarmApp.kt 작성**

```kotlin
package com.policyalarm

import android.app.Application
import com.google.firebase.FirebaseApp

class PolicyAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
```

- [ ] **Step 3: AndroidManifest.xml 업데이트**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

    <application
        android:name=".PolicyAlarmApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:theme="@style/Theme.PolicyAlarm">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <service
            android:name=".service.PolicyFcmService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT"/>
            </intent-filter>
        </service>
    </application>
</manifest>
```

- [ ] **Step 4: `res/drawable/ic_notification.xml` 생성 (벡터 아이콘)**

Android Studio → `res/drawable` 우클릭 → New → Vector Asset → 아이콘 선택 (예: notifications) → 이름: `ic_notification`

- [ ] **Step 5: 빌드 확인**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/policyalarm/service/ app/src/main/java/com/policyalarm/PolicyAlarmApp.kt app/src/main/AndroidManifest.xml
git commit -m "feat: FCM service and Application class"
```

---

### Task 7: 테마 + 네비게이션

**Files:**
- Modify: `app/src/main/java/com/policyalarm/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/policyalarm/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/policyalarm/ui/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/policyalarm/MainActivity.kt`

- [ ] **Step 1: Color.kt 작성**

```kotlin
package com.policyalarm.ui.theme

import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF1565C0)
val PrimaryVariant = Color(0xFF003C8F)
val Secondary = Color(0xFF26A69A)
val Background = Color(0xFFF5F5F5)
val Surface = Color(0xFFFFFFFF)
val OnPrimary = Color(0xFFFFFFFF)
val ReadOverlay = Color(0x40000000)
```

- [ ] **Step 2: Theme.kt 작성**

```kotlin
package com.policyalarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = Background,
    surface = Surface,
    onPrimary = OnPrimary,
)

@Composable
fun PolicyAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
```

- [ ] **Step 3: AppNavigation.kt 작성**

```kotlin
package com.policyalarm.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.policyalarm.ui.screens.detail.DetailScreen
import com.policyalarm.ui.screens.history.HistoryScreen
import com.policyalarm.ui.screens.home.HomeScreen
import com.policyalarm.ui.screens.login.LoginScreen
import com.policyalarm.ui.screens.onboarding.OnboardingScreen
import com.policyalarm.ui.screens.settings.SettingsScreen
import com.policyalarm.ui.screens.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DETAIL = "detail/{policyId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun detail(policyId: String) = "detail/$policyId"
}

@Composable
fun AppNavigation(startPolicyId: String? = null) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onLoggedIn = { navController.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onNotLoggedIn = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.SPLASH) { inclusive = true } } },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { isNewUser ->
                    if (isNewUser) navController.navigate(Routes.ONBOARDING) { popUpTo(Routes.LOGIN) { inclusive = true } }
                    else navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = { navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onPolicyClick = { policyId -> navController.navigate(Routes.detail(policyId)) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("policyId") { type = NavType.StringType })
        ) { backStack ->
            val policyId = backStack.arguments?.getString("policyId") ?: return@composable
            DetailScreen(policyId = policyId, onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onPolicyClick = { policyId -> navController.navigate(Routes.detail(policyId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
```

- [ ] **Step 4: MainActivity.kt 작성**

```kotlin
package com.policyalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.policyalarm.ui.navigation.AppNavigation
import com.policyalarm.ui.theme.PolicyAlarmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val policyId = intent.getStringExtra("policy_id")
        setContent {
            PolicyAlarmTheme {
                AppNavigation(startPolicyId = policyId)
            }
        }
    }
}
```

- [ ] **Step 5: 빌드 확인 (스크린 placeholder 필요)**

각 Screen composable 파일을 임시로 생성:

```kotlin
// SplashScreen.kt — 나머지 화면도 동일하게 임시 생성
package com.policyalarm.ui.screens.splash
import androidx.compose.runtime.*
import com.policyalarm.data.repository.UserRepository
@Composable
fun SplashScreen(onLoggedIn: () -> Unit, onNotLoggedIn: () -> Unit) {
    val repo = remember { UserRepository() }
    LaunchedEffect(Unit) { if (repo.isLoggedIn()) onLoggedIn() else onNotLoggedIn() }
}
```

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/policyalarm/ui/
git commit -m "feat: theme and navigation structure"
```

---

### Task 8: 로그인 화면

**Files:**
- Create: `app/src/main/java/com/policyalarm/ui/screens/login/LoginViewModel.kt`
- Create: `app/src/main/java/com/policyalarm/ui/screens/login/LoginScreen.kt`

- [ ] **Step 1: LoginViewModel.kt 작성**

```kotlin
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
```

- [ ] **Step 2: LoginScreen.kt 작성**

```kotlin
package com.policyalarm.ui.screens.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun LoginScreen(onLoginSuccess: (isNewUser: Boolean) -> Unit, vm: LoginViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Google로 시작하기")
            }
        }

        if (uiState is LoginUiState.Error) {
            Spacer(Modifier.height(16.dp))
            Text((uiState as LoginUiState.Error).message, color = MaterialTheme.colorScheme.error)
        }
    }
}
```

- [ ] **Step 3: `res/values/strings.xml`에 web_client_id 확인**

Firebase Console → 프로젝트 설정 → 웹 클라이언트 ID 확인 후 `google-services.json`에 포함됐는지 확인. `R.string.default_web_client_id`는 `google-services.json`에서 자동 생성됨.

- [ ] **Step 4: 빌드 확인**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/policyalarm/ui/screens/login/
git commit -m "feat: Google login screen and ViewModel"
```

---

### Task 9: 온보딩 화면 (카테고리 선택)

**Files:**
- Create: `app/src/main/java/com/policyalarm/ui/screens/onboarding/OnboardingViewModel.kt`
- Create: `app/src/main/java/com/policyalarm/ui/screens/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: OnboardingViewModel.kt 작성**

```kotlin
package com.policyalarm.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val ALL_CATEGORIES = listOf("청약", "대출", "세금", "재개발", "전월세")

class OnboardingViewModel(private val userRepo: UserRepository = UserRepository()) : ViewModel() {

    private val _selected = MutableStateFlow(setOf("청약", "대출", "세금"))
    val selected: StateFlow<Set<String>> = _selected

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    fun toggle(category: String) {
        _selected.value = if (category in _selected.value)
            _selected.value - category else _selected.value + category
    }

    fun confirm() {
        viewModelScope.launch {
            userRepo.updateSubscribedCategories(_selected.value.toList())
            _done.value = true
        }
    }
}
```

- [ ] **Step 2: OnboardingScreen.kt 작성**

```kotlin
package com.policyalarm.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OnboardingScreen(onComplete: () -> Unit, vm: OnboardingViewModel = viewModel()) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()

    LaunchedEffect(done) { if (done) onComplete() }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(48.dp))
        Text("관심 카테고리 선택", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("선택한 카테고리의 정책만 알림으로 받습니다", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        ALL_CATEGORIES.forEach { category ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = category in selected, onCheckedChange = { vm.toggle(category) })
                Spacer(Modifier.width(8.dp))
                Text(category, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = vm::confirm,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("시작하기")
        }
    }
}
```

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/policyalarm/ui/screens/onboarding/
git commit -m "feat: onboarding screen with category selection"
```

---

### Task 10: 홈 화면 (정책 피드)

**Files:**
- Create: `app/src/main/java/com/policyalarm/ui/screens/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/policyalarm/ui/screens/home/HomeScreen.kt`
- Create: `app/src/test/java/com/policyalarm/screens/HomeViewModelTest.kt`

- [ ] **Step 1: 테스트 작성**

`HomeViewModelTest.kt`:
```kotlin
package com.policyalarm.screens

import com.policyalarm.data.model.PolicyIndex
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.ui.screens.home.HomeViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockRepo = mockk<PolicyRepository>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockRepo.observeReadIds() } returns flowOf(emptyList())
    }

    @After
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `loadPolicies populates items`() = runTest {
        val items = listOf(
            PolicyItem("id-1", "부동산", "청약", "청약 개편", "국토교통부",
                "2026-05-29T09:00:00+09:00", "요약...")
        )
        coEvery { mockRepo.getPolicyIndex() } returns PolicyIndex("2026-05-29", 1, items)

        val vm = HomeViewModel(mockRepo)
        assertEquals(1, vm.uiState.value.policies.size)
        assertEquals("id-1", vm.uiState.value.policies[0].id)
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew testDebugUnitTest --tests "com.policyalarm.screens.HomeViewModelTest"
```

Expected: FAILED

- [ ] **Step 3: HomeViewModel.kt 작성**

```kotlin
package com.policyalarm.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.model.PolicyItem
import com.policyalarm.data.repository.PolicyRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val policies: List<PolicyItem> = emptyList(),
    val readIds: Set<String> = emptySet(),
    val selectedCategory: String = "전체",
    val isLoading: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val repo: PolicyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            repo.observeReadIds().collect { readIds ->
                _uiState.update { it.copy(readIds = readIds.toSet()) }
            }
        }
        loadPolicies()
    }

    fun loadPolicies() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val index = repo.getPolicyIndex()
                _uiState.update { it.copy(policies = index.items, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "불러오기 실패") }
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        if (category == "전체") {
            loadPolicies()
        } else {
            viewModelScope.launch {
                try {
                    val index = repo.getCategoryIndex(category)
                    _uiState.update { it.copy(policies = index.items) }
                } catch (_: Exception) {}
            }
        }
    }
}
```

- [ ] **Step 4: HomeScreen.kt 작성**

```kotlin
package com.policyalarm.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.policyalarm.data.model.PolicyItem

val CATEGORIES = listOf("전체", "청약", "대출", "세금", "재개발", "전월세")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPolicyClick: (String) -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("정책 알림") },
                actions = {
                    IconButton(onClick = onHistoryClick) { Icon(Icons.Default.History, "알림 기록") }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "설정") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 카테고리 탭
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(CATEGORIES) { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = { vm.selectCategory(category) },
                        label = { Text(category) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = vm::loadPolicies) { Text("다시 시도") }
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(state.policies, key = { it.id }) { policy ->
                        PolicyCard(
                            policy = policy,
                            isRead = policy.id in state.readIds,
                            onClick = { onPolicyClick(policy.id) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PolicyCard(policy: PolicyItem, isRead: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isRead) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                AssistChip(onClick = {}, label = { Text(policy.subcategory, style = MaterialTheme.typography.labelSmall) })
                Spacer(Modifier.weight(1f))
                Text(policy.publishedAt.take(10), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(policy.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(policy.summaryPreview, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(policy.source, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}
```

- [ ] **Step 5: 테스트 재실행 → 통과 확인**

```bash
./gradlew testDebugUnitTest --tests "com.policyalarm.screens.HomeViewModelTest"
```

Expected: PASSED

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/policyalarm/ui/screens/home/ app/src/test/
git commit -m "feat: home feed screen with category filter"
```

---

### Task 11: 정책 상세 화면

**Files:**
- Create: `app/src/main/java/com/policyalarm/ui/screens/detail/DetailViewModel.kt`
- Create: `app/src/main/java/com/policyalarm/ui/screens/detail/DetailScreen.kt`

- [ ] **Step 1: DetailViewModel.kt 작성**

```kotlin
package com.policyalarm.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.model.PolicyDetail
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val detail: PolicyDetail? = null,
    val isLoading: Boolean = true,
    val isBookmarked: Boolean = false,
    val error: String? = null,
)

class DetailViewModel(
    private val policyRepo: PolicyRepository,
    private val userRepo: UserRepository = UserRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState

    fun load(policyId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)
            try {
                val detail = policyRepo.getPolicyDetail(policyId)
                val bookmarked = userRepo.isBookmarked(policyId)
                policyRepo.markAsRead(policyId)
                _uiState.value = DetailUiState(detail = detail, isBookmarked = bookmarked, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = DetailUiState(isLoading = false, error = "정책을 불러올 수 없습니다")
            }
        }
    }

    fun toggleBookmark(policyId: String) {
        viewModelScope.launch {
            val bookmarked = _uiState.value.isBookmarked
            if (bookmarked) userRepo.removeBookmark(policyId) else userRepo.saveBookmark(policyId)
            _uiState.value = _uiState.value.copy(isBookmarked = !bookmarked)
        }
    }
}
```

- [ ] **Step 2: DetailScreen.kt 작성**

```kotlin
package com.policyalarm.ui.screens.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    policyId: String,
    onBack: () -> Unit,
    vm: DetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(policyId) { vm.load(policyId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("정책 상세") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } },
                actions = {
                    IconButton(onClick = { vm.toggleBookmark(policyId) }) {
                        Icon(
                            if (state.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            "북마크"
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error!!)
            }
            state.detail != null -> {
                val detail = state.detail!!
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AssistChip(onClick = {}, label = { Text(detail.subcategory) })
                    Spacer(Modifier.height(8.dp))
                    Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("${detail.source} · ${detail.publishedAt.take(10)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (detail.summary != null) {
                        Spacer(Modifier.height(24.dp))
                        SummaryCard("무엇이 바뀌었나", detail.summary.whatChanged)
                        Spacer(Modifier.height(8.dp))
                        SummaryCard("누가 대상인가", detail.summary.whoIsAffected)
                        Spacer(Modifier.height(8.dp))
                        SummaryCard("언제부터 적용되나", detail.summary.whenEffective)

                        if (detail.summary.keyPoints.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text("핵심 포인트", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            detail.summary.keyPoints.forEach { point ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("• ", style = MaterialTheme.typography.bodyMedium)
                                    Text(point, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = {
                            val url = detail.fileUrl ?: detail.sourceUrl
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("원문 보기")
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(label: String, content: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/policyalarm/ui/screens/detail/
git commit -m "feat: policy detail screen with structured summary"
```

---

### Task 12: 알림 히스토리 + 설정 화면

**Files:**
- Create: `app/src/main/java/com/policyalarm/ui/screens/history/HistoryScreen.kt`
- Create: `app/src/main/java/com/policyalarm/ui/screens/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/policyalarm/ui/screens/settings/SettingsScreen.kt`

- [ ] **Step 1: HistoryScreen.kt 작성**

```kotlin
package com.policyalarm.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.policyalarm.data.local.AppDatabase
import com.policyalarm.data.local.NotificationHistoryEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onPolicyClick: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val historyFlow: Flow<List<NotificationHistoryEntity>> = remember {
        db.notificationHistoryDao().observeAll()
    }
    val items by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("알림 기록") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("받은 알림이 없습니다")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
                items(items, key = { it.policyId }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = {
                            Text("${item.category} · ${SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(item.receivedAt))}")
                        },
                        modifier = Modifier.clickable { onPolicyClick(item.policyId) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
```

- [ ] **Step 2: SettingsViewModel.kt 작성**

```kotlin
package com.policyalarm.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.policyalarm.data.repository.UserRepository
import com.policyalarm.ui.screens.onboarding.ALL_CATEGORIES
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val subscribedCategories: Set<String> = emptySet(),
    val notificationSchedule: String = "both",
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
)

class SettingsViewModel(private val userRepo: UserRepository = UserRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = userRepo.getUserSettings() ?: return@launch
            @Suppress("UNCHECKED_CAST")
            val categories = (settings["subscribed_categories"] as? List<String>)?.toSet() ?: emptySet()
            val schedule = settings["notification_schedule"] as? String ?: "both"
            _uiState.value = SettingsUiState(subscribedCategories = categories, notificationSchedule = schedule, isLoading = false)
        }
    }

    fun toggleCategory(category: String) {
        val current = _uiState.value.subscribedCategories
        _uiState.value = _uiState.value.copy(
            subscribedCategories = if (category in current) current - category else current + category
        )
    }

    fun setSchedule(schedule: String) {
        _uiState.value = _uiState.value.copy(notificationSchedule = schedule)
    }

    fun save() {
        viewModelScope.launch {
            userRepo.updateSubscribedCategories(_uiState.value.subscribedCategories.toList())
            userRepo.updateNotificationSchedule(_uiState.value.notificationSchedule)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
```

- [ ] **Step 3: SettingsScreen.kt 작성**

```kotlin
package com.policyalarm.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.policyalarm.ui.screens.onboarding.ALL_CATEGORIES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) { if (state.isSaved) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                Text("구독 카테고리", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ALL_CATEGORIES.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = category in state.subscribedCategories, onCheckedChange = { vm.toggleCategory(category) })
                        Spacer(Modifier.width(8.dp))
                        Text(category)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("알림 수신 시간", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                listOf("morning" to "오전 9시", "evening" to "오후 6시", "both" to "둘 다").forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = state.notificationSchedule == value, onClick = { vm.setSchedule(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }

                Spacer(Modifier.weight(1f))
                Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("저장")
                }
            }
        }
    }
}
```

- [ ] **Step 4: 빌드 확인**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/policyalarm/ui/screens/history/ app/src/main/java/com/policyalarm/ui/screens/settings/
git commit -m "feat: notification history and settings screens"
```

---

### Task 13: ViewModel 팩토리 연결 및 최종 통합

**Files:**
- Create: `app/src/main/java/com/policyalarm/ui/screens/home/HomeViewModelFactory.kt`
- Create: `app/src/main/java/com/policyalarm/ui/screens/detail/DetailViewModelFactory.kt`

- [ ] **Step 1: HomeViewModelFactory.kt 작성**

```kotlin
package com.policyalarm.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.policyalarm.data.local.AppDatabase
import com.policyalarm.data.remote.RetrofitClient
import com.policyalarm.data.repository.PolicyRepository

class HomeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getInstance(context)
        val repo = PolicyRepository(RetrofitClient.policyApi, db.readPolicyDao())
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(repo) as T
    }
}
```

- [ ] **Step 2: DetailViewModelFactory.kt 작성**

```kotlin
package com.policyalarm.ui.screens.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.policyalarm.data.local.AppDatabase
import com.policyalarm.data.remote.RetrofitClient
import com.policyalarm.data.repository.PolicyRepository

class DetailViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getInstance(context)
        val repo = PolicyRepository(RetrofitClient.policyApi, db.readPolicyDao())
        @Suppress("UNCHECKED_CAST")
        return DetailViewModel(repo) as T
    }
}
```

- [ ] **Step 3: 화면에서 Factory 연결**

`HomeScreen.kt`의 `viewModel()` 호출을 아래로 변경:
```kotlin
// HomeScreen.kt — vm 파라미터 기본값 변경
import androidx.compose.ui.platform.LocalContext
// ...
vm: HomeViewModel = viewModel(factory = HomeViewModelFactory(LocalContext.current)),
```

`DetailScreen.kt`의 `viewModel()` 호출을 아래로 변경:
```kotlin
import androidx.compose.ui.platform.LocalContext
// ...
vm: DetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
    factory = DetailViewModelFactory(LocalContext.current)
),
```

- [ ] **Step 4: 전체 빌드 + 단위 테스트**

```bash
./gradlew assembleDebug testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, 모든 테스트 PASSED

- [ ] **Step 5: 에뮬레이터에서 실행 확인**

Android Studio → 에뮬레이터 실행 → 앱 설치

확인 사항:
1. 스플래시 → 로그인 화면 이동
2. Google 로그인 → 온보딩 화면 이동
3. 홈 피드 로드 (CDN이 배포된 경우 실제 데이터, 미배포 시 에러 화면)
4. 카테고리 탭 전환
5. 설정 화면에서 카테고리/시간 변경 후 저장

- [ ] **Step 6: 최종 커밋**

```bash
git add .
git commit -m "feat: Android app complete — all screens connected"
```
