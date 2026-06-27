// 정책 데이터 스키마 — Android 앱의 PolicyItem.kt / PolicyDetail.kt 와 동일한 형태.
// 데이터 원본: docs/policies/index.json, docs/policies/{id}.json (GitHub Pages CDN).

export interface PolicyItem {
  id: string;
  category: string;
  subcategory: string;
  title: string;
  source?: string;
  published_at: string;
  summary_preview: string;
}

export interface PolicyIndex {
  updated_at?: string;
  total?: number;
  items: PolicyItem[];
}

export interface PolicySummary {
  what_changed: string;
  who_is_affected: string;
  when_effective?: string | null;
  key_points?: string[];
  // 시민 가치 보강 필드(선택) — 파이프라인이 채우면 상세 페이지에 렌더된다.
  background?: string;
  eligibility?: string[];
  how_to_apply?: string | null;
  faq?: { question: string; answer: string }[];
  glossary?: { term: string; definition: string }[];
}

export interface PolicyDetail {
  id: string;
  category: string;
  subcategory: string;
  title: string;
  source: string;
  source_url: string;
  file_url?: string | null;
  file_type?: string | null;
  published_at: string;
  crawled_at: string;
  summary?: PolicySummary | null;
}

// 정책 댓글 한 건 — comments/{policyId}/items/{commentId}. (Comment.kt 와 동일)
export interface Comment {
  id: string;
  authorUid: string;
  authorNickname: string;
  text: string;
  parentId: string | null;
  mentionNickname: string | null;
  createdAtMillis: number;
  deleted: boolean;
}

export interface CommentThread {
  parent: Comment;
  replies: Comment[];
}

// 알림 한 건 — users/{uid}/notifications/{id}. (Cloud Function이 생성)
export interface NotificationItem {
  id: string;
  title: string;
  body: string;
  policyId: string;
  read: boolean;
  createdAtMillis: number;
}
