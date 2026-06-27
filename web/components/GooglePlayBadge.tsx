// 공식 "Get it on Google Play" 배지 스타일 — Play 4색 삼각형 로고 + 검정 라운드 버튼.
// Android 앱 설치(Play 스토어)로 연결.
import { PLAY_STORE_URL } from "@/lib/site";

function PlayTriangle({ className }: { className?: string }) {
  // 4색 플레이 삼각형: 좌상(파랑)·좌하(초록)·우상(빨강)·우하(노랑), 중심 C에서 만난다.
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden="true">
      <path d="M3 2.2 L3 12 L13.5 12 Z" fill="#00C3FF" />
      <path d="M3 12 L3 21.8 L13.5 12 Z" fill="#00E676" />
      <path d="M3 2.2 L13.5 12 L21.5 12 Z" fill="#FF3B30" />
      <path d="M3 21.8 L13.5 12 L21.5 12 Z" fill="#FFCE00" />
    </svg>
  );
}

export function GooglePlayBadge({ size = "md" }: { size?: "sm" | "md" }) {
  const sm = size === "sm";
  return (
    <a
      href={PLAY_STORE_URL}
      target="_blank"
      rel="noopener noreferrer"
      aria-label="Google Play에서 정책알람 앱 다운로드"
      className={`inline-flex items-center rounded-lg bg-black text-white transition hover:bg-neutral-800 ${
        sm ? "gap-1.5 px-2.5 py-1" : "gap-2.5 px-4 py-2"
      }`}
    >
      <PlayTriangle className={sm ? "h-4 w-4" : "h-6 w-6"} />
      {sm ? (
        <span className="text-[0.8rem] font-semibold leading-none">Google Play</span>
      ) : (
        <span className="flex flex-col text-left leading-none">
          <span className="text-[0.55rem] tracking-wide">GET IT ON</span>
          <span className="mt-0.5 text-[1.05rem] font-semibold tracking-tight">
            Google Play
          </span>
        </span>
      )}
    </a>
  );
}
