// 정책 댓글 Firestore 접근 — Android 앱 CommentRepository.kt 와 동일 경로/스키마.
// 경로: comments/{policyId}/items/{commentId}
"use client";

import {
  collection,
  doc,
  setDoc,
  updateDoc,
  query,
  where,
  orderBy,
  limit as fbLimit,
  startAfter,
  getDocs,
  getCountFromServer,
  serverTimestamp,
  Timestamp,
  type DocumentData,
  type QueryDocumentSnapshot,
} from "firebase/firestore";
import { getDb } from "./firebase";
import type { Comment, CommentThread } from "./types";

function items(policyId: string) {
  return collection(getDb(), "comments", policyId, "items");
}

function toComment(snap: QueryDocumentSnapshot<DocumentData>): Comment {
  const d = snap.data();
  const ts = d.createdAt as Timestamp | null | undefined;
  return {
    id: snap.id,
    authorUid: d.authorUid ?? "",
    authorNickname: d.authorNickname ?? "익명",
    text: d.text ?? "",
    parentId: d.parentId ?? null,
    mentionNickname: d.mentionNickname ?? null,
    createdAtMillis: ts ? ts.toMillis() : 0,
    deleted: d.deleted ?? false,
  };
}

/** 댓글/대댓글 작성. parentId=null 이면 최상위. 생성된 문서 id 반환. */
export async function addComment(
  policyId: string,
  authorUid: string,
  text: string,
  authorNickname: string,
  parentId: string | null = null,
  mentionNickname: string | null = null,
): Promise<string> {
  const ref = doc(items(policyId));
  await setDoc(ref, {
    authorUid,
    authorNickname,
    text,
    parentId,
    mentionNickname,
    createdAt: serverTimestamp(),
    deleted: false,
  });
  return ref.id;
}

/**
 * 최상위 댓글을 최신순 limit개 + 그 부모들의 대댓글을 함께 가져와 평탄 리스트로 반환.
 * startAfterMillis: 더보기 커서(이전 페이지 마지막 부모의 createdAt millis).
 */
export async function getComments(
  policyId: string,
  limit = 20,
  startAfterMillis?: number,
): Promise<Comment[]> {
  let q = query(
    items(policyId),
    where("parentId", "==", null),
    orderBy("createdAt", "desc"),
  );
  if (startAfterMillis != null) {
    q = query(q, startAfter(Timestamp.fromMillis(startAfterMillis)));
  }
  q = query(q, fbLimit(limit));
  const parentSnap = await getDocs(q);
  const parents = parentSnap.docs.map(toComment);
  if (parents.length === 0) return [];

  // 부모 id들의 대댓글 (Firestore "in" 최대 30개 → 청크).
  const replies: Comment[] = [];
  const parentIds = parents.map((p) => p.id);
  for (let i = 0; i < parentIds.length; i += 30) {
    const chunk = parentIds.slice(i, i + 30);
    const rs = await getDocs(
      query(items(policyId), where("parentId", "in", chunk)),
    );
    rs.docs.forEach((s) => replies.push(toComment(s)));
  }
  return [...parents, ...replies];
}

export async function softDeleteComment(policyId: string, commentId: string) {
  await updateDoc(doc(items(policyId), commentId), { deleted: true });
}

export async function commentCount(policyId: string): Promise<number> {
  const snap = await getCountFromServer(items(policyId));
  return snap.data().count;
}

// 목록 카드용 캐시 카운트 — 같은 정책은 세션당 1회만 조회하고,
// 동시 요청은 하나의 in-flight Promise 를 공유한다 (읽기 비용 절약).
const countCache = new Map<string, number>();
const countInflight = new Map<string, Promise<number>>();

export function getCachedCommentCount(policyId: string): Promise<number> {
  const cached = countCache.get(policyId);
  if (cached !== undefined) return Promise.resolve(cached);
  const inflight = countInflight.get(policyId);
  if (inflight) return inflight;
  const p = commentCount(policyId)
    .then((n) => {
      countCache.set(policyId, n);
      return n;
    })
    .finally(() => {
      countInflight.delete(policyId);
    });
  countInflight.set(policyId, p);
  return p;
}

/** 평탄 리스트를 2단계(댓글→대댓글, 각각 작성순)로 묶는다. 고아 대댓글은 버린다. */
export function groupComments(flat: Comment[]): CommentThread[] {
  const parents = flat
    .filter((c) => c.parentId == null)
    .sort((a, b) => a.createdAtMillis - b.createdAtMillis);
  const repliesByParent = new Map<string, Comment[]>();
  for (const c of flat) {
    if (c.parentId == null) continue;
    const arr = repliesByParent.get(c.parentId) ?? [];
    arr.push(c);
    repliesByParent.set(c.parentId, arr);
  }
  return parents.map((p) => ({
    parent: p,
    replies: (repliesByParent.get(p.id) ?? []).sort(
      (a, b) => a.createdAtMillis - b.createdAtMillis,
    ),
  }));
}
