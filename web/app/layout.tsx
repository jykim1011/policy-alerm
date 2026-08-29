import type { Metadata, Viewport } from "next";
import Link from "next/link";
import "./globals.css";
import { AuthProvider } from "@/components/AuthProvider";
import { Header } from "@/components/Header";
import { MobileTabBar } from "@/components/MobileTabBar";
import { SITE_DESC, SITE_NAME, SITE_URL } from "@/lib/site";
import { GooglePlayBadge } from "@/components/GooglePlayBadge";

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: `${SITE_NAME} — 정부 정책·지원금 알리미`,
    template: `%s | ${SITE_NAME}`,
  },
  description: SITE_DESC,
  applicationName: SITE_NAME,
  keywords: [
    "정부 정책",
    "정책 지원금",
    "보도자료",
    "부동산 정책",
    "청약",
    "대출",
    "복지 지원금",
    "고용 정책",
    "정책브리핑",
    "정책알람",
  ],
  authors: [{ name: SITE_NAME }],
  openGraph: {
    type: "website",
    siteName: SITE_NAME,
    locale: "ko_KR",
    url: SITE_URL,
    title: `${SITE_NAME} — 정부 정책·지원금 알리미`,
    description: SITE_DESC,
    images: [{ url: "/og-default.png", width: 1200, height: 630, alt: SITE_NAME }],
  },
  // 카카오톡/X/페이스북은 1200x630 가로 이미지를 큰 카드로 렌더한다 — 링크 클릭률에 직결.
  twitter: { card: "summary_large_image", images: ["/og-default.png"] },
  alternates: { canonical: "/" },
  appleWebApp: { capable: true, title: SITE_NAME, statusBarStyle: "default" },
  other: { "google-adsense-account": "ca-pub-4710152968528474" },
};

export const viewport: Viewport = {
  themeColor: "#1d4ed8",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className="h-full antialiased">
      <head>
        <link rel="preconnect" href="https://cdn.jsdelivr.net" crossOrigin="anonymous" />
        {/* 가독성 높은 Pretendard — 앱과 통일된 깔끔한 한글 산세리프. */}
        <link
          rel="stylesheet"
          href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable.min.css"
        />
      </head>
      <body className="min-h-full bg-paper text-ink">
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{
            __html: JSON.stringify({
              "@context": "https://schema.org",
              "@type": "WebSite",
              name: SITE_NAME,
              alternateName: "정책 알리미",
              url: SITE_URL,
              inLanguage: "ko-KR",
              description: SITE_DESC,
              publisher: {
                "@type": "Organization",
                name: SITE_NAME,
                url: SITE_URL,
                logo: `${SITE_URL}/icon-512.png`,
              },
              potentialAction: {
                "@type": "SearchAction",
                target: `${SITE_URL}/?q={search_term_string}`,
                "query-input": "required name=search_term_string",
              },
            }),
          }}
        />
        <AuthProvider>
          <Header />
          <main className="mx-auto min-h-[70vh] w-full max-w-5xl px-4 pb-24 pt-6 sm:px-6 md:pb-16 md:pt-8">
            {children}
          </main>
          <footer className="border-t border-rule bg-surface pb-24 md:pb-10">
            <div className="mx-auto max-w-5xl px-4 py-10 text-center sm:px-6">
              <p className="mb-3 text-sm font-medium text-muted">
                앱으로 새 정책 알림을 가장 먼저 받아보세요
              </p>
              <div className="flex justify-center">
                <GooglePlayBadge size="md" />
              </div>
              <nav className="mt-6 flex flex-wrap justify-center gap-x-5 gap-y-1 text-xs font-medium text-muted">
                <Link href="/about" className="hover:text-seal">소개</Link>
                <Link href="/privacy" className="hover:text-seal">개인정보처리방침</Link>
                <Link href="/contact" className="hover:text-seal">문의</Link>
              </nav>
              <p className="mt-3 text-xs text-faint">
                © 2026 정책알람 · 정부 정책 보도자료 아카이브 · 데이터 출처 정책브리핑
              </p>
            </div>
          </footer>
          <MobileTabBar />
        </AuthProvider>
      </body>
    </html>
  );
}
