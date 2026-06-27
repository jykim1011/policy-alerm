// 발행 부처 직인(도장) 스탬프 — 이 사이트의 시그니처 요소.
// 실제 정부 문서의 빨간 직인을 차용해, 정책의 출처 부처를 도장처럼 표시한다.
export function Seal({
  source,
  size = "sm",
}: {
  source?: string | null;
  size?: "sm" | "lg";
}) {
  if (!source) return null;
  return (
    <span className={size === "lg" ? "seal seal--lg" : "seal"} aria-label={`발행: ${source}`}>
      {source}
    </span>
  );
}
