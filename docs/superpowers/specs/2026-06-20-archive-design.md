# 정책 아카이브 브라우저 설계

**날짜:** 2026-06-20  
**범위:** 수집된 모든 정책을 연도·월별로 탐색할 수 있는 아카이브 화면 추가

---

## 배경

현재 홈 화면은 `index.json`의 최신 50건만 표시한다. 실제로는 219개(2026-04-06 ~ 현재) 정책 파일이 쌓여 있지만 접근 방법이 없다. 사용자가 과거 정책을 연도·월별로 찾아볼 수 있도록 아카이브 화면을 추가한다.

**범위 외 (Phase 2):** 연도별 정책 변화 비교 기능 — 멀티년도 데이터가 쌓인 뒤 별도 스펙으로 진행.

---

## 결정 사항

- 진입점: 홈 화면 상단 바 우측에 📅 아이콘 버튼 추가
- 하단 네비게이션 탭은 변경하지 않음 (홈/알림/설정 3개 유지)
- 아카이브 화면에서 정책 탭 → 기존 DetailScreen 재사용
- 연도 파일이 커질 경우(~1,000건↑)를 대비해 월별 파일로 분할 가능한 구조로 설계하되, 지금은 연도 단위 파일만 구현

---

## 데이터 레이어 (파이프라인)

### 새 파일 구조

```
docs/archive/
  index.json        ← 보유 연도 목록
  2026.json         ← 2026년 전체 정책
```

### `docs/archive/index.json`

```json
{
  "years": [2026],
  "updated_at": "2026-06-20T12:00:00+09:00"
}
```

### `docs/archive/{year}.json`

```json
{
  "year": 2026,
  "total": 219,
  "updated_at": "2026-06-20T12:00:00+09:00",
  "items": [
    {
      "id": "국토교통부-2026-06-19-abc123",
      "category": "부동산",
      "subcategory": "청약",
      "title": "청약 공급 규제 완화",
      "published_at": "2026-06-19T09:00:00+09:00",
      "summary_preview": "무엇이 바뀌었는지 100자 미리보기..."
    }
  ]
}
```

items는 `published_at` 내림차순(최신 먼저) 정렬. `summary_preview`는 기존 `index.json`의 항목 구조와 동일.

### `publisher.py` 변경

`publish_policy(item)` 에서 기존 `_update_category_index()` 호출 뒤에 `_update_archive_index(item)` 추가.

```python
def _update_archive_index(item: PolicyItem, docs_root: str = DOCS_ROOT) -> None:
    year = item.published_at[:4]          # "2026"
    archive_dir = Path(docs_root) / "archive"
    archive_dir.mkdir(parents=True, exist_ok=True)

    # 연도 파일 갱신
    year_path = archive_dir / f"{year}.json"
    if year_path.exists():
        data = json.loads(year_path.read_text(encoding="utf-8"))
    else:
        data = {"year": int(year), "total": 0, "updated_at": "", "items": []}

    entry = {
        "id": item.id,
        "category": item.category,
        "subcategory": item.subcategory,
        "title": item.title,
        "published_at": item.published_at,
        "summary_preview": (item.summary.what_changed[:100] + "...") if item.summary else "",
    }
    items = [entry] + [i for i in data["items"] if i["id"] != item.id]
    items.sort(key=lambda x: x["published_at"], reverse=True)
    data["items"] = items
    data["total"] = len(items)
    data["updated_at"] = datetime.now(KST).isoformat()
    year_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    # archive/index.json 갱신
    index_path = archive_dir / "index.json"
    if index_path.exists():
        idx = json.loads(index_path.read_text(encoding="utf-8"))
    else:
        idx = {"years": [], "updated_at": ""}
    if int(year) not in idx["years"]:
        idx["years"] = sorted(set(idx["years"] + [int(year)]), reverse=True)
    idx["updated_at"] = datetime.now(KST).isoformat()
    index_path.write_text(json.dumps(idx, ensure_ascii=False, indent=2), encoding="utf-8")
```

### 소급 생성 스크립트

기존 219개 정책 파일(`docs/policies/*.json`, index.json 제외)을 읽어 `docs/archive/2026.json`을 초기 생성하는 일회성 스크립트 `pipeline/backfill_archive.py`.

```python
# pipeline/backfill_archive.py
import json
from pathlib import Path
from publisher import _update_archive_index
from models import PolicyItem, PolicySummary

DOCS_ROOT = "docs"

policies_dir = Path(DOCS_ROOT) / "policies"
for f in sorted(policies_dir.glob("*.json")):
    if f.name == "index.json":
        continue
    d = json.loads(f.read_text(encoding="utf-8"))
    summary = None
    if d.get("summary"):
        s = d["summary"]
        summary = PolicySummary(
            what_changed=s["what_changed"],
            who_is_affected=s["who_is_affected"],
            when_effective=s["when_effective"],
            key_points=s["key_points"],
        )
    item = PolicyItem(
        id=d["id"], category=d["category"], subcategory=d["subcategory"],
        title=d["title"], source=d["source"], source_url=d.get("source_url", ""),
        file_url=d.get("file_url"), file_type=d.get("file_type"),
        published_at=d["published_at"], summary=summary,
    )
    _update_archive_index(item)
print("Backfill complete")
```

실행: `python -m pipeline.backfill_archive` (프로젝트 루트에서)

---

## Android 데이터 모델

### 새 모델 (`PolicyArchive.kt` 신규)

```kotlin
package com.policyalarm.data.model

import com.google.gson.annotations.SerializedName

data class ArchiveYears(
    val years: List<Int>,
    @SerializedName("updated_at") val updatedAt: String,
)

data class YearArchive(
    val year: Int,
    val total: Int,
    @SerializedName("updated_at") val updatedAt: String,
    val items: List<PolicyItem>,
)
```

### API 엔드포인트 (`PolicyApiService.kt` 추가)

```kotlin
@GET("archive/index.json")
suspend fun getArchiveYears(): ArchiveYears

@GET("archive/{year}.json")
suspend fun getYearArchive(@Path("year") year: Int): YearArchive
```

### Repository (`PolicyRepository.kt` 추가)

```kotlin
suspend fun getArchiveYears(): ArchiveYears = api.getArchiveYears()
suspend fun getYearArchive(year: Int): YearArchive = api.getYearArchive(year)
```

---

## Android UI

### 진입점 — `HomeScreen.kt` 상단 바

📅 아이콘 버튼을 새로고침 버튼 왼쪽에 추가. `onArchiveClick: () -> Unit` 파라미터 추가.

```
[🔲] 정책 알리미            [📅] [🔄]
```

### 네비게이션 — `AppNavigation.kt`

```kotlin
object Routes {
    // 기존 ...
    const val ARCHIVE = "archive"
}

// NavHost 내부 추가
composable(Routes.ARCHIVE) {
    ArchiveScreen(onBack = { navController.popBackStack() },
                  onPolicyClick = { navController.navigate(Routes.detail(it)) })
}
```

`MainScaffold.kt`에서 `HomeScreen`에 `onArchiveClick = { navController.navigate(Routes.ARCHIVE) }` 전달.

**문제:** `MainScaffold`는 현재 `navController`를 갖고 있지 않고 `AppNavigation`에 있다. `onArchiveClick` 콜백을 `MainScaffold → HomeScreen` 으로 전달하는 패턴을 사용한다 (기존 `onPolicyClick`과 동일한 방식).

### ArchiveScreen 레이아웃

```
← 아카이브                [2026 ▼]     ← 연도 드롭다운
─────────────────────────────────────
  2026년 6월 (42건)                    ← 월 헤더 (sticky)
  [카드] 청약 공급 규제 완화
  [카드] DSR 규제 변경
  2026년 5월 (67건)
  [카드] ...
```

**월 그룹핑**: ViewModel에서 `items`를 `published_at.substring(0,7)` (YYYY-MM) 기준으로 그룹화 후 `List<ArchiveSection>` 으로 변환. `LazyColumn`에서 stickyHeader + items 조합 렌더링.

```kotlin
data class ArchiveSection(val yearMonth: String, val items: List<PolicyItem>)
// "2026-06" → "2026년 6월 (42건)" 헤더로 표시
```

### ArchiveViewModel

```kotlin
data class ArchiveUiState(
    val availableYears: List<Int> = emptyList(),
    val selectedYear: Int = 0,
    val sections: List<ArchiveSection> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class ArchiveViewModel(private val repo: PolicyRepository) : ViewModel() {
    // init: getArchiveYears() → 첫 연도 선택 → getYearArchive(year) → sections 계산
    fun selectYear(year: Int) { /* getYearArchive(year) 재호출 */ }
}
```

---

## 변경하지 않는 것

- `DetailScreen` — 정책 상세 화면 재사용, 변경 없음
- `index.json` — 기존 홈 화면 데이터 소스 그대로 유지
- FCM / 알림 로직 — 변경 없음
- 하단 네비게이션 탭 — 변경 없음

---

## Phase 2 (데이터 충분 시)

- 동일 정책 유형의 년도별 변화 비교 (예: "청약 규제 2024 vs 2025 vs 2026")
- 비교는 AI 요약 `key_points`를 기반으로 diff 형태로 표시
- 데이터 조건: 최소 2개 연도 × 같은 카테고리 기사 보유 필요
