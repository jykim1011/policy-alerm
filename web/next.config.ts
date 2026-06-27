import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 정적 사이트로 빌드(SSG) → Firebase Hosting에 서버 없이 배포, SEO에 완벽한 프리렌더 HTML.
  output: "export",
  trailingSlash: true,
  images: { unoptimized: true },
};

export default nextConfig;
