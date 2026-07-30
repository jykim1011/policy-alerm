"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthProvider";
import { LoginGate } from "@/components/LoginGate";
import {
  getNotifications,
  markNotificationRead,
  deleteNotification,
} from "@/lib/user";
import { decodeMojibake } from "@/lib/mojibake";
import { formatRelative } from "@/lib/format";
import type { NotificationItem } from "@/lib/types";

export default function NotificationsPage() {
  return (
    <div className="mx-auto max-w-2xl">
      <header className="mb-5">
        <p className="doc-eyebrow mb-1.5">새 정책 소식</p>
        <h1 className="text-[1.6rem] font-extrabold leading-tight tracking-tight text-ink md:text-3xl">
          알림
        </h1>
      </header>
      <LoginGate>
        <NotificationList />
      </LoginGate>
    </div>
  );
}

function NotificationList() {
  const { user } = useAuth();
  const router = useRouter();
  const [items, setItems] = useState<NotificationItem[] | null>(null);

  useEffect(() => {
    if (!user) return;
    getNotifications(user.uid).then(setItems).catch(() => setItems([]));
  }, [user]);

  async function open(n: NotificationItem) {
    if (!user || !n.policyId?.trim()) return;
    if (!n.read) markNotificationRead(user.uid, n.id).catch(() => {});
    // gen2 트리거가 깨뜨린 비ASCII 정책 id 를 복원해 상세로 이동.
    router.push(`/policy/${decodeMojibake(n.policyId)}`);
  }

  async function remove(n: NotificationItem) {
    if (!user) return;
    await deleteNotification(user.uid, n.id);
    setItems((prev) => prev?.filter((x) => x.id !== n.id) ?? null);
  }

  if (items === null) {
    return <p className="py-12 text-center text-xs font-medium text-faint">불러오는 중…</p>;
  }
  if (items.length === 0) {
    return (
      <p className="card px-6 py-16 text-center text-sm text-muted">
        새 정책이 발표되면 여기에 알림이 쌓입니다.
      </p>
    );
  }

  return (
    <ul className="flex flex-col gap-2.5">
      {items.map((n) => (
        <li
          key={n.id}
          className={`card p-4 ${n.read ? "" : "bg-seal-soft"}`}
        >
          <div className="flex items-start gap-2">
            <button onClick={() => open(n)} className="flex-1 text-left">
              <p className="flex items-center gap-1.5 font-bold text-ink">
                {!n.read && (
                  <span
                    aria-label="읽지 않음"
                    className="h-1.5 w-1.5 shrink-0 rounded-full bg-seal"
                  />
                )}
                {n.title}
              </p>
              <p className="line-clamp-2 break-keep text-sm text-muted">{n.body}</p>
              <span className="text-xs font-medium text-faint">
                {formatRelative(n.createdAtMillis)}
              </span>
            </button>
            <button
              onClick={() => remove(n)}
              className="rounded-full px-2 py-1 text-xs font-semibold text-faint transition hover:bg-paper hover:text-ink"
            >
              삭제
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}
