// 사이트 전역 상수.
export const SITE_URL =
  process.env.NEXT_PUBLIC_SITE_URL ?? "https://policy-alerm.web.app";

export const SITE_NAME = "정책알람";
export const SITE_DESC =
  "부동산·청약·대출·복지·고용 등 정부 정책 보도자료를 한눈에. 새 정책이 나오면 알려드립니다.";

// 정책 상세 JSON CDN (클라이언트 측 북마크 해석 등에 사용).
export const CDN_BASE =
  process.env.NEXT_PUBLIC_CDN_BASE ??
  "https://jykim1011.github.io/policy-alerm";

// Android 앱 Play 스토어 (앱과 동일: com.policyalarm).
export const PLAY_STORE_URL =
  "https://play.google.com/store/apps/details?id=com.policyalarm";
