// 정책 본문을 다 읽은 지점에 놓는 앱 설치 유도 카드.
// 푸터 배지는 페이지 맨 아래라 거의 보이지 않으므로, 관심이 가장 높은 순간
// (해당 분야 정책 하나를 끝까지 읽은 직후)에 같은 분야의 알림을 제안한다.
import { GooglePlayBadge } from "./GooglePlayBadge";

export function AppInstallCta({ category }: { category?: string }) {
  const what = category ? `${category} 분야를 포함한 새 정책` : "새 정책";
  return (
    <aside className="card mt-6 flex flex-col items-center gap-3 bg-seal-soft px-5 py-6 text-center shadow-none">
      <p className="break-keep text-[1.02rem] font-bold text-ink">
        {what}이 나오면 바로 알려드릴까요?
      </p>
      <p className="max-w-md break-keep text-sm leading-relaxed text-ink-soft">
        관심 분야를 고르면 정부 부처 보도자료를 이렇게 요약해서 알림으로 보내드려요.
        무료이고, 원하는 시간대에만 옵니다.
      </p>
      <GooglePlayBadge size="md" />
    </aside>
  );
}
