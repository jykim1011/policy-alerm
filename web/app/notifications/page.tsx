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
    <>
      <header className="mb-6 border-b border-rule pb-4">
        <p className="doc-eyebrow mb-2">새 정책 소식</p>
        <h1 className="font-serif text-3xl font-bold leading-tight text-ink">알림</h1>
      </header>
      <LoginGate>
        <NotificationList />
      </LoginGate>
    </>
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
    if (!user) return;
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
    return <p className="py-12 text-center font-mono text-xs text-faint">불러오는 중…</p>;
  }
  if (items.length === 0) {
    return (
      <p className="py-16 text-center text-sm text-muted">
        새 정책이 발표되면 여기에 알림이 쌓입니다.
      </p>
    );
  }

  return (
    <ul className="flex flex-col gap-2">
      {items.map((n) => (
        <li
          key={n.id}
          className={`rounded-[4px] border p-3 ${
            n.read
              ? "border-rule bg-surface"
              : "border-seal bg-seal-soft"
          }`}
        >
          <div className="flex items-start gap-2">
            <button onClick={() => open(n)} className="flex-1 text-left">
              <p className="font-semibold text-ink">{n.title}</p>
              <p className="line-clamp-2 text-sm text-muted">{n.body}</p>
              <span className="font-mono text-xs text-faint">
                {formatRelative(n.createdAtMillis)}
              </span>
            </button>
            <button
              onClick={() => remove(n)}
              className="font-mono text-xs text-faint hover:text-seal"
            >
              삭제
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}
