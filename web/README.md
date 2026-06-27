# 정책알람 웹 (policy-alerm web)

Android 앱과 **같은 데이터·백엔드를 공유**하는 웹 버전. 정적 사이트(Next.js SSG)로 빌드해
Firebase Hosting 에 배포하며, SEO(검색엔진 유입)에 최적화돼 있다.

- **데이터**: 빌드 타임에 레포의 `../docs/` 정적 JSON 을 직접 읽어 정책 페이지를 프리렌더.
  목록은 마운트 후 CDN `index.json` 으로 최신 갱신(하이브리드).
- **백엔드**: Firebase(project `policy-alerm`) — Google 로그인, Firestore 댓글/북마크/알림.
  앱과 같은 컬렉션을 쓰므로 웹에서 단 댓글·북마크가 앱에 그대로 보인다.

## 로컬 개발

```bash
cd web
cp .env.local.example .env.local   # 값 채우기 (아래 "사전 준비" 참고)
npm install
npm run dev        # http://localhost:3000
```

## 빌드 / 정적 출력

```bash
npm run build      # web/out 에 정적 HTML 생성 (output: 'export')
```

`web/out/policy/{id}/index.html` 의 소스 HTML 에 `<title>`·메타·JSON-LD 가 프리렌더되어 있다(SEO 핵심).

## 사전 준비 (1회, 수동)

1. **Firebase 콘솔 → 프로젝트 `policy-alerm` → 웹 앱 등록** → `firebaseConfig` 의
   `apiKey`, `appId` 등을 `.env.local`(로컬) 및 GitHub Actions 시크릿(CI)에 입력.
2. **Authentication → Google 공급자** 의 "승인된 도메인"에 배포 도메인
   (`policy-alerm.web.app`, 커스텀 도메인) 추가.
3. **Hosting 배포용 서비스 계정**(Firebase Hosting Admin 역할) JSON 을 발급해
   CI 시크릿 `FIREBASE_HOSTING_SA` 로 등록.
4. (선택) FCM 웹 푸시를 붙일 때 클라우드 메시징의 **VAPID 키** 발급.

## 배포 (수동)

현재는 **수동 배포**가 표준이다. `web/` 에서 한 줄로 빌드+배포한다:

```bash
npm run deploy
```

(내부적으로 `next build` 로 `web/out` 생성 후 루트 `../firebase.json` 설정으로
`firebase deploy --only hosting` 실행. Firebase CLI 로그인이 되어 있어야 한다.)

### CI 자동 배포 (현재 비활성)

`.github/workflows/web-deploy.yml` 가 있지만 자동 트리거는 꺼져 있고 수동 실행만 가능하다.
켜려면 CI 서비스 계정(`firebase-adminsdk-fbsvc@…`)에 `roles/firebasehosting.admin`
권한을 부여한 뒤, 워크플로의 `push`/`workflow_run` 트리거 주석을 해제한다.
(BOM 인증 버그는 이미 수정됨.)

## 남은 작업 (follow-up)

- **FCM 웹 푸시 전송**: 알림 *히스토리* 화면(`/notifications`)은 동작하나, 웹으로의 푸시 *발송*은
  `pipeline/notifier.py`/`functions` 의 멀티 디바이스 토큰 지원 확인 후 별도 작업.
- **/privacy, /onboarding**: 개인정보처리방침은 현재 `docs/privacy.html`(Pages 도메인)로 제공 중.
  웹 도메인에서도 노출하려면 라우트 추가.
