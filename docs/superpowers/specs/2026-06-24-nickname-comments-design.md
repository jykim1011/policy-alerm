# 닉네임 + 정책 댓글/대댓글 설계 (v1.5)

작성일: 2026-06-24

## 배경 / 목표

첫 서비스 단계(사용자 30명 미만)에서 커뮤니티성·재방문 유도 기능을 추가한다.
요청 기능 4가지 중 비용·난이도를 고려해 범위를 다음과 같이 확정한다.

- ① 설정 프로필 닉네임 — **포함**
- ② 정책별 댓글 — **포함**
- ③ 대댓글(이어달기) — **포함 (2단계 평탄화)**
- ④ 현재 동시 접속 인원 표시 — **보류** (구현 난이도·미래 비용 위험이 가장 크고, 30명 규모에선 표시값이 거의 항상 0~1로 가치가 낮음. 사용자가 늘어 의미가 생길 때 RTDB + `onDisconnect` 방식으로 재검토)

### 현재 아키텍처 전제

- 인증: Google 로그인(Firebase Auth) — 실제 계정, `displayName`/`email` 보유
- 정책 데이터: GitHub Pages 정적 JSON(CDN). **Firestore를 거치지 않음** → 현재 읽기 비용 거의 0
- Firestore 사용처: `users/{uid}`(설정·FCM 토큰), `notifications`, `bookmarks`, `new_policies`(파이프라인 트리거)
- Functions: gen2, FCM 푸시 팬아웃

## ① 닉네임

- `users/{uid}` 문서에 `nickname` 필드(String) 추가. 표시 전용, **유니크 강제하지 않음**.
  - 유니크 미강제 이유: 닉네임 예약 시스템은 별도 컬렉션·트랜잭션·경쟁조건 처리가 필요해 30명 규모엔 과함.
- 설정 화면에 "닉네임" 편집 행 추가(현재값 표시 + 수정). `UserRepository.updateNickname()`.

### 자동 닉네임 생성

빈 닉네임을 "익명"으로 폴백하면 댓글에서 구분이 안 되므로, 고유성 있는 자동 닉네임을 기본값으로 둔다.

- **방식: 형용사 + 명사 + 숫자 4자리** (예: `용감한바다거북3847`, `포근한고라니0192`)
  - 형용사 ~40개 × 명사 ~40개 × 숫자 1만 ≈ 1,600만 조합 → 사실상 무충돌. 유니크 미강제이므로 충돌해도 무해.
  - 외부 라이브러리/서버 호출 없이 클라이언트에서 배열 2개 + `Random`으로 생성.
- **생성 시점·저장 (안정적 정체성 확보)**:
  - 최초 1회만 생성 → `users/{uid}.nickname`에 저장 → 이후 동일 유지.
  - 트리거: 회원가입 직후(`LoginViewModel`에서 `isNewUser`일 때 기본 카테고리 저장하는 위치)에 함께 생성·저장. 기존 사용자는 첫 댓글 작성 시 `nickname`이 없으면 생성·저장.
  - 사용자는 설정에서 언제든 변경 가능(자동 닉네임은 기본값).
- 결과: 댓글의 `authorNickname`은 항상 채워진 값으로 비정규화되며 "익명" 폴백이 불필요.

## ②③ 댓글 / 대댓글

### 데이터 모델

정책은 CDN 정적 JSON이라 쓰기가 불가하므로 Firestore에 별도 컬렉션을 신설한다.

```
comments/{policyId}/items/{commentId}
  authorUid:        String      작성자 uid
  authorNickname:   String      작성 시점 닉네임 (비정규화)
  text:             String      내용 (1~1000자)
  parentId:         String?     null = 댓글, 값 있으면 = 최상위 댓글 id (대댓글)
  mentionNickname:  String?     대댓글이 답하는 대상 닉네임 (@멘션 표시용, 선택)
  createdAt:        Timestamp   serverTimestamp
  deleted:          Boolean     소프트 삭제 플래그 (기본 false)
```

- **2단계 평탄화**: `parentId`는 항상 **최상위 댓글 id**를 가리킨다. 대댓글에 또 답해도 같은 최상위 그룹 아래에 `@닉네임` 멘션으로 깊이 1 고정으로 쌓인다. (무한 중첩 트리의 모바일 UI·조회 비용 함정 회피)
- **닉네임 비정규화**: 작성 시점 닉네임을 댓글에 박아 둔다 → 읽을 때 `users` 조회 불필요(읽기 비용↓). 닉네임 변경 시 과거 댓글은 옛 이름 유지(30명 규모에서 표준적이고 무해).
- **삭제**: 본인 댓글 소프트 삭제("삭제된 댓글입니다" 표시, 대댓글 트리 유지). **편집·신고 기능은 YAGNI로 제외**.
- **댓글 수 배지**: 상세 화면 진입 시 `count()` 집계 쿼리 1회.
- **페이지네이션**: 최상위 댓글 최초 20개 + "더보기". (미래 비용 안전장치)

### 보안 규칙 (firestore.rules 추가)

```
match /comments/{policyId}/items/{commentId} {
  // 읽기: 로그인 사용자 누구나
  allow read: if request.auth != null;

  // 생성: 본인 uid로만, 내용 1~1000자, deleted=false
  allow create: if request.auth != null
    && request.resource.data.authorUid == request.auth.uid
    && request.resource.data.text is string
    && request.resource.data.text.size() > 0
    && request.resource.data.text.size() <= 1000
    && request.resource.data.deleted == false;

  // 수정: 본인 댓글의 소프트 삭제(deleted 필드)만 허용 — 내용 편집 불가
  allow update: if request.auth != null
    && resource.data.authorUid == request.auth.uid
    && request.resource.data.diff(resource.data).affectedKeys().hasOnly(["deleted"]);

  // 하드 삭제는 클라이언트 차단 (소프트 삭제만)
  allow delete: if false;
}
```

- 작성자 위조 차단, 길이 제한으로 스토리지/악용 방어, 남의 댓글 편집 불가.
- `nickname`은 기존 `users/{uid}` 규칙(본인만 쓰기)에 포함되므로 규칙 변경 불필요.

## UI 변경

| 화면 | 변경 |
|---|---|
| 설정 | "닉네임" 편집 행 추가(현재값 표시 + 수정) |
| 정책 상세 | 하단 댓글 섹션 — 입력창, 댓글 리스트(최신순), 각 댓글 아래 대댓글 + "답글" 버튼, 본인 댓글에 "삭제" |
| 정책 상세 | 댓글 수 배지(`💬 3`) — 상세 화면에만 표시(리스트엔 미표시, 비용 절감) |

### 신규/변경 파일 (Android, Compose)

- 신규 `data/repository/CommentRepository.kt` — 댓글 CRUD + count + 페이지네이션
- 신규 `util/NicknameGenerator.kt` — 형용사/명사 배열 + 랜덤 생성
- 신규 컴포넌트 `CommentSection`, `CommentItem`, `ReplyInput`
- 변경 `DetailScreen` — 댓글 섹션 삽입
- 변경 설정 화면 — 닉네임 편집 행
- 변경 `UserRepository.kt` — `updateNickname()`, 닉네임 조회/생성 보조
- 변경 `LoginViewModel.kt` — 신규 가입 시 닉네임 생성·저장

## 비용 분석 (30명 기준 + 미래 안전장치)

| 항목 | 30명 기준 | 사용자 급증 시 위험 | 안전장치 |
|---|---|---|---|
| 닉네임 | 0 (users 필드) | 없음 | — |
| 댓글 읽기 | 상세 진입당 N건 | 인기 정책 읽기 폭증 가능 | 페이지네이션 20개 + 더보기 |
| 댓글 쓰기 | 미미 | 낮음 | 길이 1000자 제한(규칙) |
| count 배지 | 진입당 1 집계 | 중간 | 상세에서만 호출, 리스트엔 미표시 |

**결론**: 30명 규모에선 전 항목 Firebase 무료/거의 0원. 유일한 미래 리스크는 인기 정책 상세의 댓글 읽기 폭증인데, 처음부터 페이지네이션 20개로 막아두면 사용자가 수만 명이 돼도 안전. ④ presence를 보류했으므로 비용 폭탄 요인 없음.

## 테스트 전략

- `NicknameGenerator` — 생성 형식(형용사+명사+4자리), 반복 호출 시 분포가 한쪽으로 쏠리지 않음(고정 시드 단위 테스트).
- `CommentRepository` — 작성/조회/소프트삭제/페이지네이션, `parentId` 2단계 평탄화 매핑.
- firestore.rules — 에뮬레이터로 작성자 위조 거부, 길이 초과 거부, 타인 댓글 편집 거부, deleted-only update 허용 검증.

## 범위 밖 (YAGNI)

- ④ 동시 접속 인원(presence)
- 닉네임 유니크 강제/예약
- 댓글 편집, 신고, 욕설 필터, 좋아요
- 리스트 화면의 댓글 수 배지
