# 닉네임 + 정책 댓글/대댓글 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자에게 자동 생성/편집 가능한 닉네임을 부여하고, 각 정책 상세에 2단계(댓글→대댓글) 댓글 기능을 추가한다.

**Architecture:** 정책은 CDN 정적 JSON이라 댓글은 Firestore `comments/{policyId}/items/{commentId}`에 저장한다. 닉네임은 `users/{uid}.nickname`에 두고, 클라이언트에서 형용사+명사+4자리 숫자로 자동 생성한다. 순수 로직(닉네임 생성, 댓글 2단계 그룹핑)은 JVM 단위 테스트로 TDD하고, Firestore/Compose 계층은 기존 패턴을 따라 구현 후 빌드·수동 검증한다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Firebase Auth(Google), Cloud Firestore, JUnit4 + MockK + kotlinx-coroutines-test.

## Global Constraints

- 패키지 루트: `com.policyalarm` (메인), 테스트는 `com.policyalarm.*` 하위.
- ViewModel 패턴: `data class XxxUiState`, `MutableStateFlow`/`StateFlow`, `viewModelScope.launch`, 부가 작업은 `runCatching`으로 감싸 실패해도 화면 유지.
- Repository 패턴: 생성자에 `FirebaseAuth`/`FirebaseFirestore` 기본 주입, `private val uid get() = auth.currentUser?.uid ?: error("로그인 필요")`.
- 댓글 내용 길이: **1~1000자** (UI·rules 양쪽에서 강제).
- 대댓글 스레딩: **2단계 평탄화**. `parentId`는 항상 최상위 댓글 id를 가리킨다(깊이 1 고정).
- 닉네임: 표시 전용, **유니크 강제 안 함**. 자동 생성 형식 = `형용사+명사+4자리숫자`(예: `용감한바다거북3847`).
- 댓글 목록 페이지네이션: 최상위 댓글 최초 **20개** + 더보기.
- 버전: 작업 완료 시 `versionName` `1.4.15` → `1.5.0`, `versionCode` `32` → `33`.
- 색상/컴포넌트: `LocalAppColors.current`, 기존 `Emoji`, `PrimaryButton`, `SettingRow`, `SettingsSection` 재사용.

---

### Task 1: NicknameGenerator (순수 로직, TDD)

**Files:**
- Create: `android/app/src/main/java/com/policyalarm/util/NicknameGenerator.kt`
- Test: `android/app/src/test/java/com/policyalarm/util/NicknameGeneratorTest.kt`

**Interfaces:**
- Consumes: 없음 (순수 Kotlin)
- Produces: `object NicknameGenerator { fun generate(random: kotlin.random.Random = kotlin.random.Random.Default): String }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.policyalarm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NicknameGeneratorTest {

    @Test
    fun `생성된 닉네임은 형용사+명사+4자리 숫자로 끝난다`() {
        val nick = NicknameGenerator.generate(Random(0))
        // 마지막 4글자는 숫자
        val digits = nick.takeLast(4)
        assertEquals(4, digits.length)
        assertTrue("끝 4자리는 숫자여야 함: $nick", digits.all { it.isDigit() })
        // 숫자 앞에는 한글(형용사+명사)이 있어야 함
        val word = nick.dropLast(4)
        assertTrue("형용사+명사 부분이 비어있음: $nick", word.length >= 2)
        assertTrue("한글이어야 함: $nick", word.all { it in '가'..'힣' })
    }

    @Test
    fun `같은 시드는 같은 닉네임을 같은 결과로 만든다`() {
        assertEquals(NicknameGenerator.generate(Random(42)), NicknameGenerator.generate(Random(42)))
    }

    @Test
    fun `다른 시드는 대체로 다른 닉네임을 만든다`() {
        val a = NicknameGenerator.generate(Random(1))
        val b = NicknameGenerator.generate(Random(2))
        assertTrue("서로 다른 시드인데 동일: $a", a != b)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.policyalarm.util.NicknameGeneratorTest"`
Expected: FAIL — `NicknameGenerator` 미해결(unresolved reference).

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.policyalarm.util

import kotlin.random.Random

/**
 * 표시용 자동 닉네임 생성기. 형용사 + 명사 + 4자리 숫자(예: "용감한바다거북3847").
 * 유니크를 강제하지 않으므로 충돌해도 무해하다. 외부 의존성 없는 순수 함수.
 */
object NicknameGenerator {

    private val adjectives = listOf(
        "용감한", "포근한", "잔잔한", "씩씩한", "엉뚱한", "느긋한", "다정한", "어진",
        "신나는", "보드란", "차분한", "재빠른", "현명한", "솔직한", "활기찬", "기특한",
        "상냥한", "당당한", "푸른", "맑은", "따뜻한", "수줍은", "부지런한", "명랑한",
        "고요한", "달콤한", "씩씩한", "튼튼한", "유쾌한", "사랑스런", "꿋꿋한", "정다운",
        "포근한", "산뜻한", "고운", "어여쁜", "착한", "든든한", "야무진", "넉넉한",
    )

    private val nouns = listOf(
        "바다거북", "고라니", "다람쥐", "수달", "여우", "너구리", "고슴도치", "올빼미",
        "참새", "두루미", "기린", "판다", "펭귄", "돌고래", "강아지", "고양이",
        "토끼", "사슴", "오리", "거위", "두더지", "햄스터", "코알라", "원숭이",
        "북극곰", "물범", "청설모", "박새", "동박새", "딱따구리", "제비", "까치",
        "호랑이", "표범", "늑대", "사자", "코끼리", "하마", "기러기", "백조",
    )

    fun generate(random: Random = Random.Default): String {
        val adj = adjectives[random.nextInt(adjectives.size)]
        val noun = nouns[random.nextInt(nouns.size)]
        val num = random.nextInt(10000).toString().padStart(4, '0')
        return "$adj$noun$num"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.policyalarm.util.NicknameGeneratorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/policyalarm/util/NicknameGenerator.kt android/app/src/test/java/com/policyalarm/util/NicknameGeneratorTest.kt
git commit -m "feat(nickname): 자동 닉네임 생성기 (형용사+명사+4자리)"
```

---

### Task 2: UserRepository 닉네임 지원

**Files:**
- Modify: `android/app/src/main/java/com/policyalarm/data/repository/UserRepository.kt`

**Interfaces:**
- Consumes: `NicknameGenerator.generate()` (Task 1)
- Produces:
  - `suspend fun getNickname(): String?` — `users/{uid}.nickname` 조회
  - `suspend fun updateNickname(nickname: String)` — merge 저장
  - `suspend fun ensureNickname(): String` — 없으면 생성·저장 후 반환, 있으면 기존 반환

**Note:** `UserRepository`는 생성 시 Firebase에 직접 접근해 JVM 단위 테스트가 불가하다(기존 `HomeViewModelTest` 주석 참고). 이 Task는 단위 테스트 없이 구현하고, 호출하는 ViewModel(Task 3·4)에서 MockK로 동작을 검증한다.

- [ ] **Step 1: 메서드 3개 추가**

`UserRepository.kt`의 `getUserSettings()` 아래(기존 닉네임 관련 메서드 없음)에 추가:

```kotlin
    suspend fun getNickname(): String? =
        db.collection("users").document(uid).get().await().getString("nickname")

    suspend fun updateNickname(nickname: String) {
        db.collection("users").document(uid)
            .set(mapOf("nickname" to nickname), SetOptions.merge()).await()
    }

    /**
     * 닉네임이 없으면 자동 생성해 저장하고 반환한다. 이미 있으면 그대로 반환.
     * 댓글 작성 등 표시 이름이 필요한 시점에 호출해, "익명" 폴백 없이 항상 채워진 값을 보장한다.
     */
    suspend fun ensureNickname(): String {
        getNickname()?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = com.policyalarm.util.NicknameGenerator.generate()
        updateNickname(generated)
        return generated
    }
```

(상단 import에 `SetOptions`는 이미 존재 — 확인만.)

- [ ] **Step 2: 컴파일 확인**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/policyalarm/data/repository/UserRepository.kt
git commit -m "feat(nickname): UserRepository 닉네임 조회/저장/ensure 추가"
```

---

### Task 3: 회원가입 시 닉네임 자동 생성 (LoginViewModel)

**Files:**
- Modify: `android/app/src/main/java/com/policyalarm/ui/screens/login/LoginViewModel.kt`
- Test: `android/app/src/test/java/com/policyalarm/screens/LoginViewModelTest.kt`

**Interfaces:**
- Consumes: `UserRepository.ensureNickname()` (Task 2), `UserRepository.saveUserSettings(...)`
- Produces: 신규 가입(`isNewUser`) 시 `ensureNickname()` 호출이 일어남.

**Note:** `signInWithGoogle`는 `FirebaseMessaging`/`auth.signInWithCredential` 등 정적 Firebase에 의존해 그대로는 테스트가 어렵다. 닉네임 보장 로직만 떼어낸 `suspend fun ensureProfileForNewUser(isNewUser: Boolean)`을 추가하고, 이 함수만 MockK로 테스트한다.

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.policyalarm.screens.LoginViewModelTest"`
Expected: FAIL — `ensureProfileForNewUser` 미해결.

- [ ] **Step 3: Write minimal implementation**

`LoginViewModel.kt`에 메서드 추가하고, `signInWithGoogle`의 신규 가입 분기에서 호출:

```kotlin
    /** 신규 가입자에게 표시용 닉네임을 보장한다. 기존 사용자는 건드리지 않는다. */
    suspend fun ensureProfileForNewUser(isNewUser: Boolean) {
        if (isNewUser) {
            runCatching { userRepo.ensureNickname() }
        }
    }
```

`signInWithGoogle` 안, 신규 가입 시 `saveUserSettings(...)` 호출 **직후**에 추가:

```kotlin
                if (isNewUser) {
                    userRepo.saveUserSettings(
                        fcmToken = fcmToken,
                        subscribedCategories = listOf("부동산", "청약", "대출", "세금"),
                        notificationSchedule = "both",
                    )
                    ensureProfileForNewUser(isNewUser = true)
                } else {
                    userRepo.updateFcmToken(fcmToken)
                }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.policyalarm.screens.LoginViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/policyalarm/ui/screens/login/LoginViewModel.kt android/app/src/test/java/com/policyalarm/screens/LoginViewModelTest.kt
git commit -m "feat(nickname): 신규 가입 시 닉네임 자동 생성"
```

---

### Task 4: 설정 화면 닉네임 편집 (SettingsViewModel + SettingsScreen)

**Files:**
- Modify: `android/app/src/main/java/com/policyalarm/ui/screens/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/java/com/policyalarm/ui/screens/settings/SettingsScreen.kt`
- Test: `android/app/src/test/java/com/policyalarm/screens/SettingsNicknameTest.kt`

**Interfaces:**
- Consumes: `UserRepository.ensureNickname()`, `UserRepository.updateNickname(String)` (Task 2)
- Produces: `SettingsUiState.nickname: String`, `SettingsViewModel.setNickname(String)`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.policyalarm.screens

import com.policyalarm.data.repository.UserRepository
import com.policyalarm.ui.screens.settings.SettingsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsNicknameTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val userRepo = mockk<UserRepository>(relaxed = true)

    @Before fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { userRepo.getUserSettings() } returns emptyMap()
        coEvery { userRepo.ensureNickname() } returns "용감한수달1234"
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `초기 로드 시 닉네임을 채운다`() = runTest {
        val vm = SettingsViewModel(userRepo, mockk(relaxed = true))
        assertEquals("용감한수달1234", vm.uiState.value.nickname)
    }

    @Test
    fun `setNickname은 상태를 갱신하고 저장한다`() = runTest {
        val vm = SettingsViewModel(userRepo, mockk(relaxed = true))
        vm.setNickname("나의새닉")
        assertEquals("나의새닉", vm.uiState.value.nickname)
        coVerify { userRepo.updateNickname("나의새닉") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.policyalarm.screens.SettingsNicknameTest"`
Expected: FAIL — `nickname`/`setNickname` 미해결.

- [ ] **Step 3: Write minimal implementation (ViewModel)**

`SettingsUiState`에 필드 추가:

```kotlin
data class SettingsUiState(
    val subscribedCategories: Set<String> = emptySet(),
    val notificationSchedule: String = "both",
    val userName: String = "사용자",
    val userEmail: String = "",
    val nickname: String = "",
    val bookmarkCount: Int = 0,
    val isLoading: Boolean = true,
)
```

`loadSettings()`의 `_uiState.value = _uiState.value.copy(...)` 블록에 닉네임 채우기를 추가한다. `ensureNickname()`은 정지 함수이므로 같은 `viewModelScope.launch` 안에서 호출:

```kotlin
            val nickname = runCatching { userRepo.ensureNickname() }.getOrDefault("")

            _uiState.value = _uiState.value.copy(
                subscribedCategories = categories,
                notificationSchedule = schedule,
                userName = userRepo.displayName() ?: "사용자",
                userEmail = userRepo.email() ?: "",
                nickname = nickname,
                isLoading = false,
            )
```

`setSchedule` 아래에 추가:

```kotlin
    /** 닉네임을 갱신하고 즉시 저장한다(live save). */
    fun setNickname(nickname: String) {
        val trimmed = nickname.trim()
        if (trimmed.isEmpty()) return
        _uiState.value = _uiState.value.copy(nickname = trimmed)
        viewModelScope.launch {
            runCatching { userRepo.updateNickname(trimmed) }
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.policyalarm.screens.SettingsNicknameTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: 설정 화면에 닉네임 편집 행 추가 (SettingsScreen.kt)**

account header `Row` 블록(프로필 카드) 아래, "구독 카테고리" `SettingsSection` **위**에 닉네임 편집 섹션을 추가한다. 다이얼로그로 편집:

상단에 import 추가(없으면):

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Edit
```

`SettingsScreen` 컴포저블 본문에 다이얼로그 상태와 섹션 추가:

```kotlin
            var showNicknameDialog by remember { mutableStateOf(false) }

            // 닉네임
            SettingsSection("프로필") {
                SettingRow(onClick = { showNicknameDialog = true }) {
                    Emoji("🙂", 20)
                    Spacer(Modifier.width(12.dp))
                    Text("닉네임", color = c.fgStrong, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(state.nickname.ifBlank { "설정 안 됨" }, color = c.fgSubtle, fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Edit, null, tint = c.fgFaint, modifier = Modifier.size(16.dp))
                }
            }

            if (showNicknameDialog) {
                var draft by remember { mutableStateOf(state.nickname) }
                AlertDialog(
                    onDismissRequest = { showNicknameDialog = false },
                    title = { Text("닉네임 변경") },
                    text = {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { if (it.length <= 20) draft = it },
                            singleLine = true,
                            label = { Text("닉네임 (최대 20자)") },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { vm.setNickname(draft); showNicknameDialog = false },
                            enabled = draft.isNotBlank(),
                        ) { Text("저장") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNicknameDialog = false }) { Text("취소") }
                    },
                )
            }
```

(`SettingsSection`, `SettingRow`, `Emoji`는 이 파일에 이미 존재. `FontWeight`, `Icons`, `Modifier.size`도 사용 중.)

- [ ] **Step 6: 빌드 확인**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/policyalarm/ui/screens/settings/
git add android/app/src/test/java/com/policyalarm/screens/SettingsNicknameTest.kt
git commit -m "feat(nickname): 설정 화면 닉네임 표시·편집"
```

---

### Task 5: Comment 모델 + 2단계 그룹핑 (순수 로직, TDD)

**Files:**
- Create: `android/app/src/main/java/com/policyalarm/data/model/Comment.kt`
- Test: `android/app/src/test/java/com/policyalarm/model/CommentGroupingTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `data class Comment(id, authorUid, authorNickname, text, parentId: String?, mentionNickname: String?, createdAtMillis: Long, deleted: Boolean)`
  - `data class CommentThread(val parent: Comment, val replies: List<Comment>)`
  - `fun groupComments(flat: List<Comment>): List<CommentThread>` — `parentId==null`을 부모로, 나머지를 해당 부모의 replies로 묶음. 부모는 createdAt 오름차순, replies도 오름차순. 부모가 없는(고아) reply는 제외.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.policyalarm.model

import com.policyalarm.data.model.Comment
import com.policyalarm.data.model.groupComments
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentGroupingTest {

    private fun c(id: String, parent: String?, t: Long) =
        Comment(id = id, authorUid = "u", authorNickname = "닉", text = "x",
            parentId = parent, mentionNickname = null, createdAtMillis = t, deleted = false)

    @Test
    fun `최상위 댓글과 대댓글을 2단계로 묶는다`() {
        val flat = listOf(
            c("a", null, 100),
            c("b", null, 200),
            c("a1", "a", 150),
            c("a2", "a", 120),
        )
        val threads = groupComments(flat)
        assertEquals(listOf("a", "b"), threads.map { it.parent.id })   // 부모 오름차순
        assertEquals(listOf("a2", "a1"), threads[0].replies.map { it.id }) // reply 오름차순
        assertEquals(emptyList<String>(), threads[1].replies.map { it.id })
    }

    @Test
    fun `부모 없는 고아 대댓글은 제외한다`() {
        val flat = listOf(c("a", null, 100), c("x1", "ghost", 150))
        val threads = groupComments(flat)
        assertEquals(1, threads.size)
        assertEquals("a", threads[0].parent.id)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.policyalarm.model.CommentGroupingTest"`
Expected: FAIL — `Comment`/`groupComments` 미해결.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.policyalarm.data.model

/** 정책 댓글 한 건. parentId == null 이면 최상위 댓글, 값이 있으면 그 최상위 댓글의 대댓글. */
data class Comment(
    val id: String,
    val authorUid: String,
    val authorNickname: String,
    val text: String,
    val parentId: String?,
    val mentionNickname: String?,
    val createdAtMillis: Long,
    val deleted: Boolean,
)

/** 최상위 댓글과 그 대댓글 목록(깊이 1). */
data class CommentThread(
    val parent: Comment,
    val replies: List<Comment>,
)

/**
 * 평탄한 댓글 리스트를 2단계(댓글→대댓글)로 묶는다. 부모는 작성순(오름차순),
 * 각 부모의 대댓글도 작성순. 부모가 목록에 없는 고아 대댓글은 버린다.
 */
fun groupComments(flat: List<Comment>): List<CommentThread> {
    val parents = flat.filter { it.parentId == null }.sortedBy { it.createdAtMillis }
    val repliesByParent = flat.filter { it.parentId != null }
        .groupBy { it.parentId }
    return parents.map { p ->
        CommentThread(
            parent = p,
            replies = (repliesByParent[p.id] ?: emptyList()).sortedBy { it.createdAtMillis },
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.policyalarm.model.CommentGroupingTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/policyalarm/data/model/Comment.kt android/app/src/test/java/com/policyalarm/model/CommentGroupingTest.kt
git commit -m "feat(comments): Comment 모델 + 2단계 그룹핑 로직"
```

---

### Task 6: CommentRepository (Firestore)

**Files:**
- Create: `android/app/src/main/java/com/policyalarm/data/repository/CommentRepository.kt`

**Interfaces:**
- Consumes: `Comment` (Task 5), FirebaseAuth/Firestore
- Produces:
  - `suspend fun addComment(policyId: String, text: String, authorNickname: String, parentId: String? = null, mentionNickname: String? = null): String` — 생성된 commentId 반환
  - `suspend fun getComments(policyId: String, limit: Long = 20, startAfterMillis: Long? = null): List<Comment>` — 최상위는 페이지네이션 대상이지만 단순화를 위해 정책의 댓글을 createdAt 오름차순으로 한 번에 가져온다(아래 설명). limit은 최상위 페이지네이션에 사용.
  - `suspend fun softDelete(policyId: String, commentId: String)`
  - `suspend fun count(policyId: String): Int`

**Note:** Firestore 의존이라 JVM 단위 테스트 불가. 구현 후 Task 9의 수동 검증으로 확인한다. 페이지네이션은 "최상위 댓글 20개"가 기준이므로, 구현은 `parentId == null` 문서를 createdAt desc로 20개 가져오고, 그 부모 id들의 대댓글을 별도 쿼리(`whereIn` 최대 30개)로 가져와 합친다.

- [ ] **Step 1: 리포지토리 작성**

```kotlin
package com.policyalarm.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.policyalarm.data.model.Comment
import kotlinx.coroutines.tasks.await

class CommentRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val uid get() = auth.currentUser?.uid ?: error("로그인 필요")

    private fun items(policyId: String) =
        db.collection("comments").document(policyId).collection("items")

    /** 댓글/대댓글 작성. parentId=null이면 최상위 댓글. 생성된 문서 id 반환. */
    suspend fun addComment(
        policyId: String,
        text: String,
        authorNickname: String,
        parentId: String? = null,
        mentionNickname: String? = null,
    ): String {
        val ref = items(policyId).document()
        ref.set(
            mapOf(
                "authorUid" to uid,
                "authorNickname" to authorNickname,
                "text" to text,
                "parentId" to parentId,
                "mentionNickname" to mentionNickname,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "deleted" to false,
            )
        ).await()
        return ref.id
    }

    /**
     * 최상위 댓글을 최신순 limit개 가져오고, 그 부모들의 대댓글을 함께 가져와 평탄 리스트로 반환한다.
     * 호출부(ViewModel)는 groupComments()로 2단계 구조로 묶는다.
     * startAfterMillis: 더보기 페이지네이션 커서(이전 페이지 마지막 부모의 createdAt millis).
     */
    suspend fun getComments(policyId: String, limit: Long = 20, startAfterMillis: Long? = null): List<Comment> {
        var q: Query = items(policyId)
            .whereEqualTo("parentId", null)
            .orderBy("createdAt", Query.Direction.DESCENDING)
        if (startAfterMillis != null) q = q.startAfter(com.google.firebase.Timestamp(startAfterMillis / 1000, 0))
        val parentDocs = q.limit(limit).get().await().documents
        val parents = parentDocs.map { it.toComment() }
        if (parents.isEmpty()) return emptyList()

        // 부모 id들의 대댓글 (Firestore whereIn 최대 30개 → 청크)
        val replies = mutableListOf<Comment>()
        val parentIds = parents.map { it.id }
        for (i in parentIds.indices step 30) {
            val chunk = parentIds.subList(i, minOf(i + 30, parentIds.size))
            val snap = items(policyId).whereIn("parentId", chunk).get().await()
            snap.documents.forEach { replies.add(it.toComment()) }
        }
        return parents + replies
    }

    suspend fun softDelete(policyId: String, commentId: String) {
        items(policyId).document(commentId).update("deleted", true).await()
    }

    suspend fun count(policyId: String): Int =
        items(policyId).count().get(AggregateSource.SERVER).await().count.toInt()

    private fun com.google.firebase.firestore.DocumentSnapshot.toComment(): Comment {
        val ts = getTimestamp("createdAt")
        return Comment(
            id = id,
            authorUid = getString("authorUid") ?: "",
            authorNickname = getString("authorNickname") ?: "익명",
            text = getString("text") ?: "",
            parentId = getString("parentId"),
            mentionNickname = getString("mentionNickname"),
            createdAtMillis = ts?.toDate()?.time ?: 0L,
            deleted = getBoolean("deleted") ?: false,
        )
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`count()` Aggregation은 firebase-firestore 24.4+ 필요 — 빌드 실패 시 버전 확인.)

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/policyalarm/data/repository/CommentRepository.kt
git commit -m "feat(comments): CommentRepository (작성/조회/소프트삭제/카운트)"
```

---

### Task 7: firestore.rules 댓글 규칙 + 배포

**Files:**
- Modify: `firestore.rules`

**Interfaces:**
- Consumes: 없음
- Produces: `comments/{policyId}/items/{commentId}` 읽기/쓰기 규칙

- [ ] **Step 1: 규칙 추가**

`firestore.rules`의 `new_policies` match 블록 아래, 최상위 `match /databases/{database}/documents` 닫기 전에 추가:

```
    // 정책 댓글 — 로그인 사용자 읽기, 본인 작성/소프트삭제만
    match /comments/{policyId}/items/{commentId} {
      allow read: if request.auth != null;

      allow create: if request.auth != null
        && request.resource.data.authorUid == request.auth.uid
        && request.resource.data.text is string
        && request.resource.data.text.size() > 0
        && request.resource.data.text.size() <= 1000
        && request.resource.data.deleted == false;

      // 본인 댓글의 deleted 플래그만 수정 허용 (내용 편집 불가)
      allow update: if request.auth != null
        && resource.data.authorUid == request.auth.uid
        && request.resource.data.diff(resource.data).affectedKeys().hasOnly(["deleted"]);

      allow delete: if false;
    }
```

- [ ] **Step 2: 배포**

Run: `firebase deploy --only firestore:rules`
Expected: `✔ Deploy complete!`

- [ ] **Step 3: Commit**

```bash
git add firestore.rules
git commit -m "feat(comments): firestore 댓글 보안 규칙"
```

---

### Task 8: 댓글 UI + DetailViewModel 통합

**Files:**
- Modify: `android/app/src/main/java/com/policyalarm/ui/screens/detail/DetailViewModel.kt`
- Create: `android/app/src/main/java/com/policyalarm/ui/screens/detail/CommentSection.kt`
- Modify: `android/app/src/main/java/com/policyalarm/ui/screens/detail/DetailScreen.kt`
- Test: `android/app/src/test/java/com/policyalarm/screens/DetailCommentsTest.kt`

**Interfaces:**
- Consumes: `CommentRepository` (Task 6), `UserRepository.ensureNickname()` (Task 2), `groupComments` + `CommentThread` (Task 5)
- Produces: `DetailUiState`에 `commentThreads: List<CommentThread>`, `commentCount: Int`; `DetailViewModel.loadComments(policyId)`, `postComment(policyId, text, parentId, mentionNickname)`, `deleteComment(policyId, commentId)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.policyalarm.screens

import com.policyalarm.data.model.Comment
import com.policyalarm.data.repository.CommentRepository
import com.policyalarm.data.repository.PolicyRepository
import com.policyalarm.data.repository.UserRepository
import com.policyalarm.ui.screens.detail.DetailViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailCommentsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val policyRepo = mockk<PolicyRepository>(relaxed = true)
    private val userRepo = mockk<UserRepository>(relaxed = true)
    private val commentRepo = mockk<CommentRepository>(relaxed = true)

    @Before fun setup() { Dispatchers.setMain(testDispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `loadComments는 평탄 리스트를 2단계 스레드로 묶는다`() = runTest {
        coEvery { commentRepo.getComments("p1") } returns listOf(
            Comment("a", "u", "닉", "부모", null, null, 100, false),
            Comment("a1", "u", "닉", "자식", "a", "닉", 150, false),
        )
        val vm = DetailViewModel(policyRepo, userRepo, commentRepo)

        vm.loadComments("p1")

        val threads = vm.uiState.value.commentThreads
        assertEquals(1, threads.size)
        assertEquals("a", threads[0].parent.id)
        assertEquals(listOf("a1"), threads[0].replies.map { it.id })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.policyalarm.screens.DetailCommentsTest"`
Expected: FAIL — 3번째 생성자 인자/`loadComments`/`commentThreads` 미해결.

- [ ] **Step 3: DetailViewModel 확장**

`DetailViewModel.kt`를 수정한다. 생성자에 `commentRepo` 추가, UiState에 필드 추가, 메서드 추가:

```kotlin
import com.policyalarm.data.model.CommentThread
import com.policyalarm.data.model.groupComments
import com.policyalarm.data.repository.CommentRepository
```

```kotlin
data class DetailUiState(
    val detail: PolicyDetail? = null,
    val isLoading: Boolean = true,
    val isBookmarked: Boolean = false,
    val error: String? = null,
    val commentThreads: List<CommentThread> = emptyList(),
    val commentCount: Int = 0,
    val myUid: String? = null,
)
```

```kotlin
class DetailViewModel(
    private val policyRepo: PolicyRepository,
    private val userRepo: UserRepository = UserRepository(),
    private val commentRepo: CommentRepository = CommentRepository(),
) : ViewModel() {
```

`load()` 끝(상세 로드 성공 후)에 `loadComments(policyId)` 호출을 추가하고, 아래 메서드들을 클래스에 추가:

```kotlin
    fun loadComments(policyId: String) {
        viewModelScope.launch {
            runCatching {
                val flat = commentRepo.getComments(policyId)
                val count = runCatching { commentRepo.count(policyId) }.getOrDefault(flat.size)
                groupComments(flat) to count
            }.onSuccess { (threads, count) ->
                _uiState.value = _uiState.value.copy(
                    commentThreads = threads,
                    commentCount = count,
                    myUid = userRepo.uidOrNull(),
                )
            }
        }
    }

    fun postComment(policyId: String, text: String, parentId: String? = null, mentionNickname: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 1000) return
        viewModelScope.launch {
            runCatching {
                val nickname = userRepo.ensureNickname()
                commentRepo.addComment(policyId, trimmed, nickname, parentId, mentionNickname)
            }.onSuccess { loadComments(policyId) }
        }
    }

    fun deleteComment(policyId: String, commentId: String) {
        viewModelScope.launch {
            runCatching { commentRepo.softDelete(policyId, commentId) }
                .onSuccess { loadComments(policyId) }
        }
    }
```

`UserRepository.kt`에 보조 메서드 추가(테스트의 `myUid` 확인용, Task 2 파일):

```kotlin
    fun uidOrNull(): String? = auth.currentUser?.uid
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.policyalarm.screens.DetailCommentsTest"`
Expected: PASS (1 test).

- [ ] **Step 5: CommentSection 컴포저블 작성**

```kotlin
package com.policyalarm.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.policyalarm.data.model.Comment
import com.policyalarm.data.model.CommentThread
import com.policyalarm.ui.theme.LocalAppColors

/**
 * 정책 상세 하단 댓글 영역. 입력창 + 2단계(댓글→대댓글) 목록.
 * reply 대상이 선택되면 그 부모 id/닉네임으로 대댓글을 단다.
 */
@Composable
fun CommentSection(
    threads: List<CommentThread>,
    commentCount: Int,
    myUid: String?,
    onPost: (text: String, parentId: String?, mentionNickname: String?) -> Unit,
    onDelete: (commentId: String) -> Unit,
) {
    val c = LocalAppColors.current
    var input by remember { mutableStateOf("") }
    // (부모 id, 멘션 닉네임) — null이면 최상위 댓글
    var replyTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("댓글 $commentCount", color = c.fgStrong, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        replyTarget?.let { (_, nick) ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text("@$nick 님에게 답글", color = c.accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("취소", color = c.fgSubtle, fontSize = 12.sp, modifier = Modifier.clickable { replyTarget = null })
            }
        }

        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 1000) input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("댓글을 입력하세요") },
                maxLines = 4,
            )
            Spacer(Modifier.height(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.Send, "등록",
                tint = if (input.isBlank()) c.fgFaint else c.accent,
                modifier = Modifier
                    .padding(start = 8.dp, top = 12.dp)
                    .clickable(enabled = input.isNotBlank()) {
                        onPost(input, replyTarget?.first, replyTarget?.second)
                        input = ""
                        replyTarget = null
                    },
            )
        }

        Spacer(Modifier.height(16.dp))
        threads.forEach { thread ->
            CommentRow(thread.parent, myUid, isReply = false,
                onReply = { replyTarget = thread.parent.id to thread.parent.authorNickname },
                onDelete = { onDelete(thread.parent.id) })
            thread.replies.forEach { reply ->
                CommentRow(reply, myUid, isReply = true,
                    onReply = { replyTarget = thread.parent.id to reply.authorNickname },
                    onDelete = { onDelete(reply.id) })
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    myUid: String?,
    isReply: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 20.dp else 0.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.bgSurface)
            .padding(12.dp),
    ) {
        Text(comment.authorNickname, color = c.fgStrong, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        if (comment.deleted) {
            Text("삭제된 댓글입니다", color = c.fgFaint, fontSize = 13.sp)
        } else {
            val body = comment.mentionNickname?.let { "@$it ${comment.text}" } ?: comment.text
            Text(body, color = c.fgDefault, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row {
                Text("답글", color = c.fgSubtle, fontSize = 11.5.sp, modifier = Modifier.clickable { onReply() })
                if (comment.authorUid == myUid) {
                    Spacer(Modifier.height(0.dp))
                    Text("삭제", color = c.danger, fontSize = 11.5.sp,
                        modifier = Modifier.padding(start = 14.dp).clickable { onDelete() })
                }
            }
        }
    }
}
```

(`c.danger`, `c.fgFaint`, `c.bgSurface` 등은 `LocalAppColors`에 존재 — `DetailScreen.kt`/`SettingsScreen.kt`에서 사용 중. 없으면 가장 가까운 색으로 대체.)

- [ ] **Step 6: DetailScreen에 댓글 섹션 삽입**

`DetailScreen.kt`의 본문 `Column`(verticalScroll) 안, AI disclaimer 아래 `Spacer(Modifier.height(16.dp))` **다음**에 추가:

```kotlin
                    CommentSection(
                        threads = state.commentThreads,
                        commentCount = state.commentCount,
                        myUid = state.myUid,
                        onPost = { text, parentId, mention -> vm.postComment(policyId, text, parentId, mention) },
                        onDelete = { commentId -> vm.deleteComment(policyId, commentId) },
                    )
                    Spacer(Modifier.height(24.dp))
```

- [ ] **Step 7: 전체 단위 테스트 + 빌드**

Run: `cd android && ./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: 모든 테스트 PASS, BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/com/policyalarm/ui/screens/detail/ android/app/src/main/java/com/policyalarm/data/repository/UserRepository.kt android/app/src/test/java/com/policyalarm/screens/DetailCommentsTest.kt
git commit -m "feat(comments): 정책 상세 댓글/대댓글 UI + DetailViewModel 통합"
```

---

### Task 9: 버전 업 + 기기 수동 검증

**Files:**
- Modify: `android/app/build.gradle.kts:25-26`

**Interfaces:** 없음 (릴리스 메타)

- [ ] **Step 1: 버전 상향**

`build.gradle.kts`:

```kotlin
        versionCode = 33
        versionName = "1.5.0"
```

- [ ] **Step 2: 디버그 빌드 설치**

Run: `cd android && ./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL, 기기/에뮬레이터에 설치.

- [ ] **Step 3: 수동 검증 체크리스트 (Firestore 실연동)**

다음을 직접 확인한다:
- [ ] 신규 계정으로 로그인 → 설정 화면 "프로필 > 닉네임"에 자동 닉네임(형용사+명사+숫자) 표시
- [ ] 닉네임 변경 다이얼로그에서 수정·저장 → 재진입 시 유지
- [ ] 정책 상세 하단에 "댓글 N" + 입력창 표시
- [ ] 댓글 작성 → 목록에 내 닉네임으로 즉시 표시, "댓글 N" 증가
- [ ] 댓글의 "답글" → `@닉네임` 프리픽스로 대댓글 작성, 부모 아래 들여쓰기 표시
- [ ] 내 댓글에만 "삭제" 노출 → 삭제 시 "삭제된 댓글입니다"로 바뀌고 대댓글 유지
- [ ] (선택) 다른 계정에서 같은 정책 댓글이 보이는지 확인

- [ ] **Step 4: Commit**

```bash
git add android/app/build.gradle.kts
git commit -m "chore(android): 버전 1.4.15 → 1.5.0 (versionCode 33) — 닉네임/댓글"
```

---

## Self-Review

**Spec coverage:**
- ① 닉네임 설정 → Task 1(생성), 2(저장), 3(가입 시), 4(설정 UI) ✅
- 빈 닉네임 랜덤 생성 → Task 1 + `ensureNickname` (Task 2) ✅
- ② 정책별 댓글 → Task 5(모델), 6(repo), 7(rules), 8(UI) ✅
- ③ 대댓글(2단계 평탄화) → Task 5(grouping), 6(parentId), 8(@멘션 UI) ✅
- ④ 동시접속 인원 → 보류(스펙 명시), 계획에서 의도적으로 제외 ✅
- 보안 규칙 → Task 7 ✅
- 페이지네이션 20개 → Task 6 `getComments(limit=20)` ✅ (UI "더보기" 버튼은 후속 — 최초 20개로 비용 상한은 이미 달성. 20개 초과 시 추가 로드 UI는 v1.5.1 후속으로 남김)
- 비용 안전장치(count는 상세에서만) → Task 6/8 ✅

**Placeholder scan:** "TBD/TODO/적절히" 없음. 모든 코드 스텝에 실제 코드 포함 ✅

**Type consistency:**
- `NicknameGenerator.generate(Random)` — Task 1 정의, Task 2에서 `generate()` 기본인자 호출 ✅
- `ensureNickname(): String` — Task 2 정의, Task 3·4·8 호출 ✅
- `groupComments(List<Comment>): List<CommentThread>` — Task 5 정의, Task 8 사용 ✅
- `Comment` 필드(`createdAtMillis`, `mentionNickname`, `parentId`) — Task 5·6·8 일치 ✅
- `CommentRepository.getComments/addComment/softDelete/count` 시그니처 — Task 6 정의, Task 8 호출 일치 ✅
- `uidOrNull()` — Task 8에서 `UserRepository`에 추가 명시 ✅

**알려진 가정/리스크:**
- `count()` Aggregation 쿼리는 firebase-firestore 24.4+ 필요. Task 6 Step 2에서 빌드 실패 시 BoM 버전 확인.
- `getComments`의 `startAfter` 커서 변환(`Timestamp(millis/1000, 0)`)은 더보기 UI 미구현 상태에선 미사용 경로 — 후속 작업에서 정밀화.
- firestore.rules의 `parentId == null` 저장: 클라이언트가 `null`을 명시 저장하므로 `whereEqualTo("parentId", null)` 쿼리가 동작. (Firestore는 null 값 필드도 인덱싱)
