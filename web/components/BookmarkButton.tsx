// 북마크 토글 버튼 (로그인 필요).
"use client";

import { useEffect, useState } from "react";
import { useAuth } from "./AuthProvider";
import { isBookmarked, saveBookmark, removeBookmark } from "@/lib/user";

export function BookmarkButton({ policyId }: { policyId: string }) {
  const { user, signIn } = useAuth();
  const [marked, setMarked] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!user) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setMarked(false);
      return;
    }
    isBookmarked(user.uid, policyId).then(setMarked).catch(() => {});
  }, [user, policyId]);

  async function toggle() {
    if (!user) {
      await signIn();
      return;
    }
    setBusy(true);
    try {
      if (marked) {
        await removeBookmark(user.uid, policyId);
        setMarked(false);
      } else {
        await saveBookmark(user.uid, policyId);
        setMarked(true);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <button
      onClick={toggle}
      disabled={busy}
      className={`rounded-[4px] border px-3 py-1.5 text-sm font-medium transition disabled:opacity-50 ${
        marked
          ? "border-seal bg-seal-soft text-seal"
          : "border-rule-strong bg-paper text-ink-soft hover:border-seal hover:text-seal"
      }`}
    >
      {marked ? "★ 보관함에 있음" : "☆ 보관함에 담기"}
    </button>
  );
}
