// 정적 export 의 404 페이지 (out/404.html).
// /policy/{id} 경로라면 빌드에 아직 없는 새 정책일 수 있으므로, CDN 폴백이
// 정책 JSON 을 직접 불러와 상세를 렌더한다 (데이터 갱신 ↔ 웹 재배포 사이 공백 대응).
import type { Metadata } from "next";
import { PolicyCdnFallback } from "@/components/PolicyCdnFallback";

export const metadata: Metadata = {
  title: "페이지를 찾을 수 없습니다",
  robots: { index: false },
};

export default function NotFound() {
  return <PolicyCdnFallback />;
}
