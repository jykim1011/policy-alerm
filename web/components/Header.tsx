// 상단 헤더 — 흰색 스티키 바. 블루 로고 마크 + 볼드 제호 + 우측 네비/로그인.
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "./AuthProvider";
import { GooglePlayBadge } from "./GooglePlayBadge";
import { SITE_NAME } from "@/lib/site";

const NAV = [
  { href: "/bookmarks", label: "보관함" },
  { href: "/notifications", label: "알림" },
  { href: "/settings", label: "설정" },
];

export function Header() {
  const { user, nickname, loading, signIn, logout } = useAuth();
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-20 border-b border-rule bg-surface/90 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-5xl items-center gap-2 px-4 sm:h-16 sm:px-6">
        <Link href="/" className="flex shrink-0 items-center gap-2">
          {/* 앱 대표 아이콘을 그대로 사용 (Android 런처 아이콘과 동일). */}
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/icon-192.png"
            alt=""
            width={32}
            height={32}
            className="h-8 w-8 rounded-[10px] shadow-sm"
          />
          <span className="text-[1.08rem] font-extrabold tracking-tight text-ink sm:text-lg">
            {SITE_NAME}
          </span>
        </Link>

        <nav className="ml-auto flex items-center gap-1.5 sm:gap-2">
          {/* 메인 네비 — 모바일은 하단 탭바가 담당 */}
          <div className="hidden items-center md:flex">
            {NAV.map((n) => {
              const active = pathname?.startsWith(n.href);
              return (
                <Link
                  key={n.href}
                  href={n.href}
                  className={`rounded-lg px-3 py-2 text-sm font-semibold transition ${
                    active
                      ? "text-ink"
                      : "text-muted hover:bg-paper hover:text-ink"
                  }`}
                >
                  {n.label}
                </Link>
              );
            })}
          </div>

          <span className="hidden sm:block">
            <GooglePlayBadge size="sm" />
          </span>

          {loading ? null : user ? (
            <div className="flex items-center gap-1.5">
              {nickname && (
                <span className="hidden items-center gap-1.5 rounded-full bg-paper py-1 pl-1 pr-2.5 sm:inline-flex">
                  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-seal text-[0.7rem] font-bold text-white">
                    {Array.from(nickname.trim())[0] ?? "익"}
                  </span>
                  <span className="text-[0.8rem] font-semibold text-ink-soft">
                    {nickname}
                  </span>
                </span>
              )}
              <button
                onClick={() => logout()}
                className="rounded-full px-2.5 py-1.5 text-xs font-semibold text-faint transition hover:bg-paper hover:text-ink"
              >
                로그아웃
              </button>
            </div>
          ) : (
            <button
              onClick={() => signIn()}
              className="rounded-full bg-seal px-4 py-2 text-[0.82rem] font-bold text-white transition hover:bg-seal-ink"
            >
              로그인
            </button>
          )}
        </nav>
      </div>
    </header>
  );
}
