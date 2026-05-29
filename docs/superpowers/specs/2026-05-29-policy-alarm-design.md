# 정책 알림 앱 (Policy Alarm) — 설계 문서

**작성일:** 2026-05-29  
**플랫폼:** Android  
**개발 형태:** 1인 개발  
**목표:** 국내 정부 정책(부동산 중심) 발표 시 사용자에게 푸시 알림을 보내고, 구조화된 요약과 원문 링크를 제공하는 Android 앱

---

## 1. 문제 정의

정부 정책은 각 부처 웹사이트에 게시되지만, 일반 사용자가 이를 능동적으로 확인하기 어렵다. 특히 부동산 정책은 변동이 잦고 생활에 직접 영향을 미치지만, 발표 사실 자체를 모르는 경우가 많다. 이 앱은 신규 정책 발표를 자동 감지하여 사용자에게 즉시 알리고, AI로 요약된 핵심 내용을 제공한다.

---

## 2. 핵심 요구사항

- 신규 정책 발표 시 Android 푸시 알림 수신
- 알림 탭 시 정책 상세 화면으로 이동
- 상세 화면: 구조화 요약 (무엇이 바뀌었나 / 누가 대상인가 / 언제부터) + 원문 링크
- 카테고리별 구독 설정 (관심 없는 카테고리 알림 차단)
- 알림 수신 시간 선택 (오전 9시 / 오후 6시 / 둘 다)
- Google 로그인으로 기기 변경 시에도 설정 유지
- 서버 비용 최소화 (월 ~$0 목표)

---

## 3. 시스템 아키텍처

### 전체 구조

```
[GitHub Actions - cron]
  └─ crawler.py
  └─ extractor.py   (HWP / HWPX / PDF / HTML)
  └─ summarizer.py  (Claude Haiku API)
  └─ publisher.py   (gh-pages CDN 배포)
  └─ notifier.py    (Firestore 새 정책 ID 기록)
       │
       └─ Firestore onWrite 트리거
            └─ Firebase Cloud Function
                 └─ FCM multicast 발송
                      │
                      └─ Android 앱
                           └─ CDN에서 정책 JSON fetch
```

### 레이어별 역할

| 레이어 | 기술 | 비용 |
|--------|------|------|
| 크롤링 + 요약 파이프라인 | GitHub Actions (Python) | 무료 |
| 정책 데이터 CDN | GitHub Pages | 무료 |
| 인증 | Firebase Auth (Google Sign-In) | 무료 |
| 유저 상태 저장 | Firebase Firestore | 무료 티어 |
| 푸시 알림 | Firebase Cloud Messaging (FCM) | 무료 |
| AI 요약 | Claude Haiku API | ~$0.001/건 |
| Android 앱 | Kotlin + Jetpack Compose | - |

---

## 4. 데이터 파이프라인

### 실행 스케줄

- GitHub Actions cron: 매일 **오전 9시 (KST)**, **오후 6시 (KST)**

### 파이프라인 단계

```
1. crawler.py
   - 정책 출처 사이트 크롤링 (국토교통부, 정책브리핑 등)
   - 신규 URL 감지: seen_policies.json과 해시 비교
   - 신규 건만 다음 단계로 전달

2. extractor.py
   - 첨부파일 타입 감지 후 텍스트 추출
   - .hwpx → zipfile + xml.etree (표준 라이브러리)
   - .hwp  → hwp5 라이브러리
   - .pdf  → pymupdf
   - .html → beautifulsoup4
   - 파싱 실패 시 → 페이지 HTML 본문으로 폴백

3. summarizer.py
   - 추출된 텍스트를 Claude Haiku API에 전달
   - 구조화 요약 생성:
     - what_changed: 무엇이 바뀌었나
     - who_is_affected: 누가 대상인가
     - when_effective: 언제부터 적용되나
     - key_points: 핵심 포인트 3~5개 (불릿)

4. publisher.py
   - policy JSON 파일 생성
   - gh-pages 브랜치에 푸시 → GitHub Pages CDN 자동 배포
   - index.json 및 카테고리별 index.json 갱신

5. notifier.py
   - Firebase Admin SDK로 Firestore에 새 정책 ID 기록
   - Cloud Function이 onWrite 트리거로 FCM 발송
```

### 크롤링 대상 사이트 (초기)

- 국토교통부 보도자료 (molit.go.kr)
- 정책브리핑 부동산 섹션 (korea.kr)
- LH 한국토지주택공사 (lh.or.kr)
- 청약홈 공지사항 (applyhome.co.kr)

---

## 5. 데이터 구조

### CDN 파일 구조

```
/policies/index.json              ← 전체 최근 50건 목록
/policies/{id}.json               ← 개별 정책 상세
/categories/부동산/index.json      ← 카테고리별 목록
/categories/청약/index.json
/categories/대출/index.json
/categories/세금/index.json
/categories/재개발/index.json
/categories/전월세/index.json
```

### 개별 정책 JSON 스키마

```json
{
  "id": "molit-2026-05-29-001",
  "category": "부동산",
  "subcategory": "청약",
  "title": "2026년 하반기 청약 제도 개편 방안",
  "source": "국토교통부",
  "source_url": "https://www.molit.go.kr/...",
  "file_url": "https://www.molit.go.kr/files/policy.hwp",
  "file_type": "hwp",
  "published_at": "2026-05-29T09:00:00+09:00",
  "crawled_at": "2026-05-29T09:12:00+09:00",
  "summary": {
    "what_changed": "무주택 실수요자 청약 가점 우대 폭을 기존 5점에서 10점으로 확대",
    "who_is_affected": "무주택 기간 3년 이상 세대주, 부양가족 1인 이상",
    "when_effective": "2026년 7월 1일 이후 청약 접수분부터 적용",
    "key_points": [
      "가점제 적용 비율 85% → 90%로 상향",
      "특별공급 소득 기준 완화",
      "생애최초 특별공급 물량 10% 추가 확대"
    ]
  }
}
```

### index.json 스키마

```json
{
  "updated_at": "2026-05-29T09:15:00+09:00",
  "total": 127,
  "items": [
    {
      "id": "molit-2026-05-29-001",
      "category": "부동산",
      "subcategory": "청약",
      "title": "2026년 하반기 청약 제도 개편 방안",
      "source": "국토교통부",
      "published_at": "2026-05-29T09:00:00+09:00",
      "summary_preview": "무주택 실수요자 청약 가점 우대 폭 확대..."
    }
  ]
}
```

### 카테고리 구조

```
부동산
 ├─ 청약/분양
 ├─ 대출/금리 (LTV·DSR)
 ├─ 세금 (취득세·종부세)
 ├─ 재개발/재건축
 └─ 전월세
```

---

## 6. Android 앱

### 화면 플로우

```
스플래시
  └─ 로그인 (Google)
       └─ 온보딩 (카테고리 구독 선택) ← 최초 1회
            └─ 홈 (메인 피드)
                 ├─ 정책 상세
                 │    └─ 원문 링크 (외부 브라우저)
                 ├─ 알림 히스토리
                 └─ 설정
                      ├─ 구독 카테고리 관리
                      └─ 알림 수신 시간 선택
```

### 화면별 상세

**홈 피드**
- CDN `index.json` fetch → 카드 리스트 표시
- 카테고리 탭 필터 (전체 / 청약 / 대출 / 세금 / 재개발 / 전월세)
- 읽은 정책은 흐리게 표시 (Room DB에 읽은 ID 로컬 저장)
- Pull-to-refresh로 최신 목록 갱신

**정책 상세**
- CDN `{id}.json` fetch
- 구조화 요약 3개 섹션 카드 표시
- 핵심 포인트 불릿 리스트
- "원문 보기" 버튼 → CustomTabs로 브라우저 열기
- 북마크 버튼 → Firestore에 저장

**알림 히스토리**
- FCM 수신 시 Room DB에 자동 기록
- 정책 제목 + 수신 시각 리스트

**설정**
- 구독 카테고리 토글 → Firestore 동기화
- 알림 수신 시간: 오전 9시 / 오후 6시 / 둘 다 선택
  - GitHub Actions는 항상 9시·18시 두 번 실행되며, 이 설정은 Cloud Function이 FCM 발송 시 유저의 `notification_schedule` 값을 확인해 해당 배치가 아닌 유저는 발송을 건너뜀으로써 구현

### 기술 스택

| 역할 | 기술 |
|------|------|
| UI | Jetpack Compose |
| 네비게이션 | Navigation Compose |
| 네트워크 | Retrofit + OkHttp |
| 로컬 DB | Room (읽은 기록, 알림 히스토리) |
| 인증 | Firebase Auth + Google Sign-In |
| 푸시 | Firebase Cloud Messaging |
| 유저 상태 | Firestore |
| 아키텍처 | MVVM + Repository 패턴 |

---

## 7. 알림 발송 플로우

### FCM 페이로드

```json
{
  "notification": {
    "title": "새 부동산 정책",
    "body": "2026년 하반기 청약 제도 개편 방안"
  },
  "data": {
    "policy_id": "molit-2026-05-29-001",
    "category": "부동산",
    "subcategory": "청약"
  }
}
```

앱은 `policy_id`로 CDN URL을 조립하여 상세 JSON을 fetch한다. FCM 페이로드에 정책 본문을 담지 않아 FCM 제한(4KB)을 피한다.

### 중복 발송 방지

- `seen_policies.json`: 처리된 정책 URL 해시 목록, GitHub 저장소에 커밋으로 유지
- Firestore: 정책 ID 기준 이중 체크 (Cloud Function 단계)

---

## 8. Firestore 데이터 구조

```
users/{uid}
  ├─ fcm_token: string
  ├─ subscribed_categories: string[]   // ["청약", "대출"]
  └─ notification_schedule: string     // "morning" | "evening" | "both"

users/{uid}/bookmarks/{policy_id}
  └─ bookmarked_at: timestamp

new_policies/{policy_id}              // notifier.py가 기록 → Cloud Function onWrite 트리거 → FCM 발송 후 문서 삭제
  ├─ category: string
  ├─ subcategory: string
  ├─ title: string
  └─ batch: string                    // "morning" | "evening" — Cloud Function이 유저 schedule과 매칭에 사용
```

---

## 9. 비용 분석

| 항목 | 비용 |
|------|------|
| GitHub Actions (하루 2회 크론) | 무료 |
| GitHub Pages CDN | 무료 |
| Firebase Auth | 무료 |
| Firebase Firestore | 무료 티어 (50K reads/20K writes/일) |
| Firebase Cloud Functions | 무료 티어 (125K 호출/월) |
| FCM 푸시 발송 | 무료 |
| Claude Haiku API (신규 정책 1건) | ~$0.001 |
| **월 예상 합계** | **~$0** |

---

## 10. 확장 가능성

- 카테고리 추가: 새 크롤러 + 카테고리 JSON 파일 추가만으로 확장 (청년 지원, 창업, 복지 등)
- 정책 비교: 동일 주제 정책의 변경 이력 표시
- 개인화: 유저 프로필(무주택자, 1주택자 등) 기반 관련도 하이라이트
