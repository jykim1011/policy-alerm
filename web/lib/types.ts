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
  sectors?: string[];
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

// 시장 영향 — 파이프라인 pipeline/market.py 가 붙인다. 업종은 LLM, ETF·기업은 사람이 관리하는 매핑표.
export interface Ticker {
  code: string;
  name: string;
}

export interface MarketSector {
  sector: string;
  direction: "긍정" | "부정" | "혼재";
  reason: string;
  etfs: Ticker[];
  stocks: Ticker[];
}

export interface PolicyMarket {
  sectors: MarketSector[];
  policy_type: string;
  /** 신규 | 확대 | 연장 | 재확인 */
  novelty?: string;
  /** 검토·계획 | 발표 | 입법예고 | 의결·통과 | 시행 */
  stage?: string;
  figures?: { label: string; value: string }[];
  /** YYYY-MM-DD 또는 YYYY-MM */
  effective_date?: string;
  /** 높음 | 보통 | 낮음 — 파이프라인 market.impact_of 규칙으로 계산 */
  impact?: string;
  impact_score?: number;
  investor_note?: string;
}

/** 카드를 보여줄 중요도. 파이프라인 market.SHOW_IMPACT 와 같은 값. */
export const SHOW_IMPACT = ["높음", "보통"];

// docs/market/sector_stats.json — 업종별 과거 정책 발표 후 5거래일 반응 집계.
export interface ReactionAgg {
  count: number;
  avg_d5?: number | null;
  avg_x5?: number | null;
  up_ratio_d5?: number;
  avg_d20?: number | null;
}

export interface SectorStats extends ReactionAgg {
  by_direction: Record<string, ReactionAgg>;
  recent: { policy_id: string; title: string; date: string; direction: string; d5?: number }[];
}

// docs/market/reactions.json 항목 — 이 정책 발표 후 실제 움직임.
export interface PolicyReaction {
  sector: string;
  d1?: number;
  d5?: number;
  d20?: number;
  x5?: number;
}

export interface Disclosure {
  title: string;
  date: string;
  url: string;
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
  market?: PolicyMarket | null;
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
