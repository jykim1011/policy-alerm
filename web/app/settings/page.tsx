"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/components/AuthProvider";
import { LoginGate } from "@/components/LoginGate";
import { getNickname, updateNickname } from "@/lib/user";

export default function SettingsPage() {
  return (
    <div className="mx-auto max-w-2xl">
      <header className="mb-5">
        <p className="doc-eyebrow mb-1.5">내 계정</p>
        <h1 className="text-[1.6rem] font-extrabold leading-tight tracking-tight text-ink md:text-3xl">
          설정
        </h1>
      </header>
      <LoginGate>
        <SettingsForm />
      </LoginGate>
    </div>
  );
}

function SettingsForm() {
  const { user, refreshNickname } = useAuth();
  const [nick, setNick] = useState("");
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!user) return;
    getNickname(user.uid).then((n) => setNick(n ?? "")).catch(() => {});
  }, [user]);

  async function save() {
    if (!user) return;
    const v = nick.trim();
    if (!v) return;
    setBusy(true);
    try {
      await updateNickname(user.uid, v);
      await refreshNickname();
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card p-5 sm:p-7">
      <label className="mb-1 block text-sm font-bold text-ink">닉네임</label>
      <p className="mb-2.5 text-xs text-muted">의견에 표시되는 이름입니다.</p>
      <div className="flex gap-2">
        <input
          value={nick}
          onChange={(e) => setNick(e.target.value)}
          maxLength={20}
          className="min-w-0 flex-1 rounded-xl bg-paper px-3.5 py-2.5 text-sm text-ink outline-none ring-seal/60 transition focus:ring-2"
        />
        <button
          onClick={save}
          disabled={busy}
          className="shrink-0 rounded-full bg-seal px-5 py-2 text-sm font-bold text-white transition hover:bg-seal-ink disabled:opacity-50"
        >
          저장
        </button>
      </div>
      {saved && <p className="mt-2 text-xs font-semibold text-seal">저장되었습니다.</p>}

      <hr className="my-5 border-rule" />
      <div className="flex items-center justify-between gap-3 text-sm">
        <span className="text-xs font-semibold text-faint">계정</span>
        <span className="truncate text-ink-soft">{user?.email}</span>
      </div>
    </div>
  );
}
