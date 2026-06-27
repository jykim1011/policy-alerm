// 북마크 id 목록을 실제 정책 상세로 해석하고, 사라진(404) 고아 북마크는 제거한다.
// Android 앱 BookmarkResolver.kt 의 resolveBookmarks 와 동일한 정책.
"use client";

import { CDN_BASE } from "./site";
import { getBookmarkIds, removeBookmark } from "./user";
import type { PolicyDetail } from "./types";

export async function resolveBookmarks(uid: string): Promise<PolicyDetail[]> {
  const ids = await getBookmarkIds(uid);
  const results = await Promise.all(
    ids.map(async (id) => {
      try {
        const res = await fetch(`${CDN_BASE}/policies/${encodeURIComponent(id)}.json`);
        if (res.status === 404) {
          // 정책 JSON이 더 이상 없는 고아 북마크 — 제거해 목록/개수를 정리.
          await removeBookmark(uid, id).catch(() => {});
          return null;
        }
        if (!res.ok) return null; // 일시 오류 — 이번엔 제외하되 삭제하지 않음.
        return (await res.json()) as PolicyDetail;
      } catch {
        return null;
      }
    }),
  );
  return results.filter((p): p is PolicyDetail => p !== null);
}
