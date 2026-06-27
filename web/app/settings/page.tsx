"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/components/AuthProvider";
import { LoginGate } from "@/components/LoginGate";
import { getNickname, updateNickname } from "@/lib/user";

export default function SettingsPage() {
  return (
    <>
      <header className="mb-6 border-b border-rule pb-4">
        <p className="doc-eyebrow mb-2">내 계정</p>
        <h1 className="font-serif text-3xl font-bold leading-tight text-ink">설정</h1>
      </header>
      <LoginGate>
        <SettingsForm />
      </LoginGate>
    </>
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
    <div className="rounded-[4px] border border-rule-strong bg-paper p-5">
      <label className="mb-1.5 block text-sm font-medium text-ink">닉네임</label>
      <p className="mb-2 text-xs text-muted">의견에 표시되는 이름입니다.</p>
      <div className="flex gap-2">
        <input
          value={nick}
          onChange={(e) => setNick(e.target.value)}
          maxLength={20}
          className="flex-1 rounded-[3px] border border-rule bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-seal"
        />
        <button
          onClick={save}
          disabled={busy}
          className="rounded-md bg-seal px-4 py-2 text-sm font-medium text-white hover:bg-seal-ink disabled:opacity-50"
        >
          저장
        </button>
      </div>
      {saved && <p className="mt-2 font-mono text-xs text-seal">저장되었습니다.</p>}

      <hr className="my-5 border-rule" />
      <div className="flex items-center justify-between text-sm">
        <span className="font-mono text-xs text-faint">계정</span>
        <span className="text-ink-soft">{user?.email}</span>
      </div>
    </div>
  );
}
