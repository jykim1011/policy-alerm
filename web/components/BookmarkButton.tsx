// 북마크 토글 버튼 (로그인 필요).
"use client";

import { useEffect, useState } from "react";
import { useAuth } from "./AuthProvider";
import { isBookmarked, saveBookmark, removeBookmark } from "@/lib/user";
import { BookmarkIcon } from "./icons";

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
      className={`inline-flex items-center gap-1.5 rounded-full px-4 py-2 text-sm font-semibold transition disabled:opacity-50 ${
        marked
          ? "bg-seal-soft text-seal"
          : "bg-paper text-ink-soft hover:bg-rule"
      }`}
    >
      <BookmarkIcon filled={marked} className="h-4 w-4" />
      {marked ? "보관함에 있음" : "보관함에 담기"}
    </button>
  );
}
