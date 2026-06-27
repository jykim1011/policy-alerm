import type { Metadata } from "next";
import "./globals.css";
import { AuthProvider } from "@/components/AuthProvider";
import { Header } from "@/components/Header";
import { SITE_DESC, SITE_NAME, SITE_URL } from "@/lib/site";
import { GooglePlayBadge } from "@/components/GooglePlayBadge";

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: `${SITE_NAME} — 정부 정책·지원금 알리미`,
    template: `%s | ${SITE_NAME}`,
  },
  description: SITE_DESC,
  openGraph: {
    type: "website",
    siteName: SITE_NAME,
    locale: "ko_KR",
    url: SITE_URL,
    title: `${SITE_NAME} — 정부 정책·지원금 알리미`,
    description: SITE_DESC,
  },
  twitter: { card: "summary_large_image" },
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
        <AuthProvider>
          <Header />
          <main className="mx-auto min-h-[70vh] max-w-3xl border-rule bg-surface px-5 py-8 md:border-x">
            {children}
          </main>
          <footer className="mx-auto max-w-3xl px-5 py-10 text-center">
            <p className="mb-3 text-sm text-muted">
              앱으로 새 정책 알림을 가장 먼저 받아보세요
            </p>
            <div className="flex justify-center">
              <GooglePlayBadge size="md" />
            </div>
            <p className="mt-6 text-xs text-faint">
              정책알람 · 정부 정책 보도자료 아카이브 · 데이터 출처 정책브리핑
            </p>
          </footer>
        </AuthProvider>
      </body>
    </html>
  );
}
