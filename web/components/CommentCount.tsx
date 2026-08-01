// 목록 카드의 댓글 수 배지 — 카드가 화면에 들어올 때만 Firestore 집계를
// 조회한다(IntersectionObserver). 세션 캐시로 같은 정책은 1회만 읽는다.
"use client";

import { useEffect, useRef, useState } from "react";
import { getCachedCommentCount } from "@/lib/comments";
import { ChatIcon } from "./icons";

export function CommentCount({
  policyId,
  className = "",
}: {
  policyId: string;
  className?: string;
}) {
  const [count, setCount] = useState<number | null>(null);
  const ref = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    let alive = true;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          io.disconnect();
          getCachedCommentCount(policyId)
            .then((n) => {
              if (alive) setCount(n);
            })
            .catch(() => {});
        }
      },
      { rootMargin: "200px" },
    );
    io.observe(el);
    return () => {
      alive = false;
      io.disconnect();
    };
  }, [policyId]);

  return (
    <span
      ref={ref}
      className={`inline-flex items-center gap-1 text-xs font-medium text-faint ${className}`}
      aria-label={count !== null ? `댓글 ${count}개` : undefined}
    >
      {count !== null && (
        <>
          <ChatIcon className="h-3.5 w-3.5" />
          {count}
        </>
      )}
    </span>
  );
}
