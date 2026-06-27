// 로그인 필요한 화면 공통 게이트.
"use client";

import { useAuth } from "./AuthProvider";

export function LoginGate({ children }: { children: React.ReactNode }) {
  const { user, loading, signIn } = useAuth();

  if (loading) {
    return <p className="py-12 text-center font-mono text-xs text-faint">불러오는 중…</p>;
  }
  if (!user) {
    return (
      <div className="py-16 text-center">
        <p className="mb-4 text-sm text-muted">
          로그인하면 보관함·알림·의견을 이용할 수 있어요.
        </p>
        <button
          onClick={() => signIn()}
          className="rounded-md bg-seal px-4 py-2 font-medium text-white hover:bg-seal-ink"
        >
          Google 로그인
        </button>
      </div>
    );
  }
  return <>{children}</>;
}
