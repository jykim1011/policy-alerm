// 제호(masthead) — 관보 제호 형식. 명조 제호 + 직인 괘선 + mono 네비.
"use client";

import Link from "next/link";
import { useAuth } from "./AuthProvider";
import { GooglePlayBadge } from "./GooglePlayBadge";
import { SITE_NAME } from "@/lib/site";

export function Header() {
  const { user, nickname, loading, signIn, logout } = useAuth();

  return (
    <header className="sticky top-0 z-20 bg-paper/95 backdrop-blur">
      <div className="mx-auto max-w-3xl px-5 pt-4">
        <div className="flex flex-wrap items-baseline gap-x-3 gap-y-2">
          <Link
            href="/"
            className="font-serif text-xl font-bold tracking-tight text-ink sm:text-2xl"
          >
            {SITE_NAME}
          </Link>
          <span className="hidden font-mono text-[0.68rem] text-faint sm:inline">
            政策 보도자료
          </span>

          <nav className="ml-auto flex flex-wrap items-center justify-end gap-x-3 gap-y-1.5 text-xs text-muted sm:gap-x-4">
            <GooglePlayBadge size="sm" />
            <Link href="/bookmarks" className="hover:text-seal">보관함</Link>
            <Link href="/notifications" className="hover:text-seal">알림</Link>
            <Link href="/settings" className="hover:text-seal">설정</Link>
            {loading ? null : user ? (
              <span className="flex items-center gap-2">
                {nickname && (
                  <span className="hidden items-center gap-1.5 rounded-full bg-seal-soft py-0.5 pl-1 pr-2 sm:inline-flex">
                    <span className="flex h-5 w-5 items-center justify-center rounded-full bg-seal text-[0.65rem] font-bold text-white">
                      {Array.from(nickname.trim())[0] ?? "익"}
                    </span>
                    <span className="text-[0.78rem] font-semibold text-seal-ink">
                      {nickname}
                    </span>
                  </span>
                )}
                <button onClick={() => logout()} className="hover:text-seal">
                  로그아웃
                </button>
              </span>
            ) : (
              <button
                onClick={() => signIn()}
                className="rounded-md bg-seal px-3 py-1 font-medium text-white hover:bg-seal-ink"
              >
                로그인
              </button>
            )}
          </nav>
        </div>
        <div className="rule-seal mt-3" />
      </div>
    </header>
  );
}
