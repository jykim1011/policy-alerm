// 사용자 데이터(닉네임/북마크/알림) Firestore 접근 — Android 앱 UserRepository.kt 포팅.
"use client";

import {
  doc,
  getDoc,
  setDoc,
  deleteDoc,
  collection,
  getDocs,
  updateDoc,
  type DocumentData,
} from "firebase/firestore";
import { getDb } from "./firebase";
import { generateNickname } from "./nickname";
import type { NotificationItem } from "./types";

export async function getNickname(uid: string): Promise<string | null> {
  const snap = await getDoc(doc(getDb(), "users", uid));
  return (snap.data()?.nickname as string | undefined) ?? null;
}

export async function updateNickname(uid: string, nickname: string) {
  await setDoc(doc(getDb(), "users", uid), { nickname }, { merge: true });
}

/** 닉네임이 없으면 자동 생성·저장 후 반환. 이미 있으면 그대로. */
export async function ensureNickname(uid: string): Promise<string> {
  const existing = await getNickname(uid);
  if (existing && existing.trim()) return existing;
  const generated = generateNickname();
  await updateNickname(uid, generated);
  return generated;
}

// ---- 북마크: users/{uid}/bookmarks/{policyId} ----

function bookmarks(uid: string) {
  return collection(getDb(), "users", uid, "bookmarks");
}

export async function saveBookmark(uid: string, policyId: string) {
  await setDoc(doc(bookmarks(uid), policyId), {
    bookmarked_at: Date.now(),
  });
}

export async function removeBookmark(uid: string, policyId: string) {
  await deleteDoc(doc(bookmarks(uid), policyId));
}

export async function isBookmarked(
  uid: string,
  policyId: string,
): Promise<boolean> {
  const snap = await getDoc(doc(bookmarks(uid), policyId));
  return snap.exists();
}

export async function getBookmarkIds(uid: string): Promise<string[]> {
  const snap = await getDocs(bookmarks(uid));
  return snap.docs.map((d) => d.id);
}

// ---- 알림: users/{uid}/notifications/{id} (생성은 Cloud Function 전용) ----

function notifications(uid: string) {
  return collection(getDb(), "users", uid, "notifications");
}

export async function getNotifications(
  uid: string,
): Promise<NotificationItem[]> {
  const snap = await getDocs(notifications(uid));
  const list = snap.docs.map((d) => {
    const x = d.data() as DocumentData;
    const createdAt = x.createdAt;
    const millis =
      createdAt && typeof createdAt.toMillis === "function"
        ? createdAt.toMillis()
        : (x.createdAtMillis ?? 0);
    return {
      id: d.id,
      title: x.title ?? "",
      body: x.body ?? "",
      policyId: x.policy_id ?? x.policyId ?? "",
      read: x.read ?? false,
      createdAtMillis: millis,
    } as NotificationItem;
  });
  return list.sort((a, b) => b.createdAtMillis - a.createdAtMillis);
}

export async function markNotificationRead(uid: string, notifId: string) {
  await updateDoc(doc(notifications(uid), notifId), { read: true });
}

export async function deleteNotification(uid: string, notifId: string) {
  await deleteDoc(doc(notifications(uid), notifId));
}
