# 배포 전 남은 작업 (TODO)

## 완료된 작업
- [x] GitHub push (https://github.com/jykim1011/policy-alerm)
- [x] GitHub Pages 활성화 → https://jykim1011.github.io/policy-alerm/
- [x] CDN_BASE_URL 설정 (android/app/build.gradle.kts)
- [x] Firebase Android 앱 등록 (App ID: 1:249888568978:android:38d587fc8683945c7a984b)
- [x] google-services.json 실제 파일 교체 및 커밋

---

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
