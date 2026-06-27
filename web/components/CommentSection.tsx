// 정책 의견(댓글/대댓글) 섹션 — Android 앱 CommentSection.kt 대응.
"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "./AuthProvider";
import {
  addComment,
  getComments,
  groupComments,
  softDeleteComment,
} from "@/lib/comments";
import type { CommentThread } from "@/lib/types";
import { formatRelative } from "@/lib/format";

export function CommentSection({ policyId }: { policyId: string }) {
  const { user, nickname, signIn, refreshNickname } = useAuth();
  const [threads, setThreads] = useState<CommentThread[]>([]);
  const [loading, setLoading] = useState(true);
  const [text, setText] = useState("");
  const [replyTo, setReplyTo] = useState<{ id: string; nick: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const flat = await getComments(policyId, 50);
      setThreads(groupComments(flat));
    } catch {
      setThreads([]);
    } finally {
      setLoading(false);
    }
  }, [policyId]);

  useEffect(() => {
    // 마운트 시 의견 1회 로드 (setState 는 await 이후 비동기 콜백에서만 발생).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  async function submit() {
    if (!user) {
      await signIn();
      return;
    }
    const body = text.trim();
    if (!body) return;
    setBusy(true);
    try {
      const nick = nickname ?? "익명";
      await addComment(
        policyId,
        user.uid,
        body,
        nick,
        replyTo?.id ?? null,
        replyTo?.nick ?? null,
      );
      setText("");
      setReplyTo(null);
      await refreshNickname();
      await load();
    } finally {
      setBusy(false);
    }
  }

  async function remove(commentId: string) {
    await softDeleteComment(policyId, commentId);
    await load();
  }

  const totalCount = threads.reduce((n, t) => n + 1 + t.replies.length, 0);

  return (
    <section className="mt-10">
      <p className="doc-eyebrow mb-3">시민 의견 {totalCount}</p>

      <div className="mb-6 rounded-[4px] border border-rule-strong bg-paper p-3">
        {replyTo && (
          <div className="mb-2 flex items-center gap-2 font-mono text-xs text-muted">
            <span>↳ {replyTo.nick} 님에게 답글</span>
            <button onClick={() => setReplyTo(null)} className="text-seal">
              취소
            </button>
          </div>
        )}
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          maxLength={1000}
          rows={2}
          placeholder={
            user ? "이 정책에 대한 의견을 남겨주세요" : "로그인하고 의견을 남겨주세요"
          }
          className="w-full resize-none rounded-[3px] border border-rule bg-surface px-3 py-2 text-sm text-ink outline-none placeholder:text-faint focus:border-seal"
        />
        <div className="mt-2 flex justify-end">
          <button
            onClick={submit}
            disabled={busy}
            className="rounded-md bg-seal px-4 py-1.5 text-sm font-medium text-white hover:bg-seal-ink disabled:opacity-50"
          >
            {user ? "등록" : "로그인"}
          </button>
        </div>
      </div>

      {loading ? (
        <p className="font-mono text-xs text-faint">불러오는 중…</p>
      ) : threads.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted">
          이 정책에 첫 의견을 남겨보세요.
        </p>
      ) : (
        <ul className="flex flex-col gap-5">
          {threads.map((t) => (
            <li key={t.parent.id}>
              <CommentRow
                nickname={t.parent.authorNickname}
                text={t.parent.deleted ? "삭제된 의견입니다." : t.parent.text}
                millis={t.parent.createdAtMillis}
                deleted={t.parent.deleted}
                mine={user?.uid === t.parent.authorUid}
                onReply={() =>
                  setReplyTo({ id: t.parent.id, nick: t.parent.authorNickname })
                }
                onDelete={() => remove(t.parent.id)}
              />
              {t.replies.length > 0 && (
                <ul className="mt-3 flex flex-col gap-3 border-l-2 border-seal-soft pl-4">
                  {t.replies.map((r) => (
                    <li key={r.id}>
                      <CommentRow
                        nickname={r.authorNickname}
                        text={r.deleted ? "삭제된 의견입니다." : r.text}
                        millis={r.createdAtMillis}
                        deleted={r.deleted}
                        mine={user?.uid === r.authorUid}
                        mention={r.mentionNickname}
                        onDelete={() => remove(r.id)}
                      />
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function CommentRow({
  nickname,
  text,
  millis,
  deleted,
  mine,
  mention,
  onReply,
  onDelete,
}: {
  nickname: string;
  text: string;
  millis: number;
  deleted: boolean;
  mine: boolean;
  mention?: string | null;
  onReply?: () => void;
  onDelete?: () => void;
}) {
  const initial = Array.from(nickname.trim())[0] ?? "익";
  return (
    <div className="flex gap-2.5">
      <span
        aria-hidden="true"
        className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-seal-soft text-[0.8rem] font-bold text-seal-ink ring-1 ring-seal/15"
      >
        {initial}
      </span>
      <div className="min-w-0 flex-1">
        <div className="mb-1 flex items-center gap-2">
          <span className="text-[0.9rem] font-bold text-seal-ink">{nickname}</span>
          {mine && (
            <span className="rounded-full bg-seal-soft px-1.5 py-px text-[0.65rem] font-medium text-seal-ink">
              나
            </span>
          )}
          <span className="text-xs text-faint">{formatRelative(millis)}</span>
          {!deleted && mine && (
            <button onClick={onDelete} className="ml-auto text-xs text-faint hover:text-seal">
              삭제
            </button>
          )}
        </div>
        <p className="whitespace-pre-wrap text-[0.95rem] leading-relaxed text-ink-soft">
          {mention && <span className="font-semibold text-seal">@{mention} </span>}
          {text}
        </p>
        {!deleted && onReply && (
          <button
            onClick={onReply}
            className="mt-1 text-xs text-faint hover:text-seal"
          >
            답글
          </button>
        )}
      </div>
    </div>
  );
}
