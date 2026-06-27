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

## 배포 (CI 자동)

`.github/workflows/web-deploy.yml` 가 다음 시점에 빌드+배포한다:
- `web/**` 또는 `firebase.json` 변경 push (코드/설정 변경)
- **Policy Pipeline** 워크플로 완료 후(`workflow_run`) — 데이터 갱신 반영
  (파이프라인 데이터 커밋은 `[skip ci]` 라 push 트리거가 안 걸리므로 필요)

필요 시크릿: `FIREBASE_HOSTING_SA`, `NEXT_PUBLIC_FIREBASE_API_KEY`,
`NEXT_PUBLIC_FIREBASE_APP_ID`. (project id·sender id·auth domain 등 비밀 아님 → 워크플로에 인라인.)

수동 배포:

```bash
npm run build
npx firebase-tools deploy --only hosting --project policy-alerm
```

## 남은 작업 (follow-up)

- **FCM 웹 푸시 전송**: 알림 *히스토리* 화면(`/notifications`)은 동작하나, 웹으로의 푸시 *발송*은
  `pipeline/notifier.py`/`functions` 의 멀티 디바이스 토큰 지원 확인 후 별도 작업.
- **/privacy, /onboarding**: 개인정보처리방침은 현재 `docs/privacy.html`(Pages 도메인)로 제공 중.
  웹 도메인에서도 노출하려면 라우트 추가.
