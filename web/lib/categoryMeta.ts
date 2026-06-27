// 카테고리/파일타입 메타 — Android 앱의 ui/components/CategoryMeta.kt 를 포팅.

export interface CategoryMeta {
  key: string;
  emoji: string;
  full: string;
}

export const CATEGORY_LIST: CategoryMeta[] = [
  { key: "전체", emoji: "📑", full: "전체" },
  { key: "부동산", emoji: "🏠", full: "부동산 전체" },
  { key: "청약", emoji: "🔑", full: "청약 / 분양" },
  { key: "대출", emoji: "🏦", full: "대출 / 금리" },
  { key: "세금", emoji: "🧾", full: "세금 (취득세·종부세)" },
  { key: "재개발", emoji: "🏗️", full: "재개발 / 재건축" },
  { key: "전월세", emoji: "🏘️", full: "전·월세" },
  { key: "고용", emoji: "💼", full: "고용 / 취업" },
  { key: "복지", emoji: "🤝", full: "복지 / 지원금" },
  { key: "창업", emoji: "🚀", full: "창업 / 소상공인" },
  { key: "육아", emoji: "👶", full: "육아 / 보육" },
  { key: "교육", emoji: "📚", full: "교육 / 장학" },
  { key: "금융", emoji: "📈", full: "금융 / 투자" },
];

// "전체" 필터를 제외한 구독 가능한 카테고리.
export const SUBSCRIBABLE_CATEGORIES = CATEGORY_LIST.slice(1);

export function catMeta(key: string): CategoryMeta {
  return CATEGORY_LIST.find((c) => c.key === key) ?? CATEGORY_LIST[0];
}

export function catEmoji(key: string): string {
  return catMeta(key).emoji;
}

export interface FileMeta {
  label: string;
  // Tailwind 텍스트/배경에 쓸 hex (Color.kt 의 File* 토큰).
  color: string;
}

export function fileMeta(type?: string | null): FileMeta | null {
  switch (type?.toLowerCase()) {
    case "hwp":
      return { label: "HWP", color: "#2563EB" };
    case "hwpx":
      return { label: "HWPX", color: "#2563EB" };
    case "pdf":
      return { label: "PDF", color: "#DC2626" };
    case "html":
      return { label: "HTML", color: "#16A34A" };
    default:
      return null;
  }
}
