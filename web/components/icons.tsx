// 정책알람 커스텀 아이콘 세트 — 전부 직접 그린 SVG.
// 디자인 언어: 24px 그리드, 1.7px 라운드 스트로크, currentColor 단색에
// 12% 듀오톤 필(면)을 깔아 깊이를 준다. 색은 사용처의 텍스트 색을 따른다.
import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement> & { strokeWidth?: number };

function Base({ children, strokeWidth = 1.7, ...rest }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...rest}
    >
      {children}
    </svg>
  );
}

/** 듀오톤 면 — 스트로크 없는 12% 필. */
function Tone({ d }: { d: string }) {
  return <path d={d} fill="currentColor" opacity=".12" stroke="none" />;
}

/* ================= 카테고리 아이콘 ================= */

function AllIcon(p: IconProps) {
  return (
    <Base {...p}>
      <rect x="4.2" y="4.2" width="6.6" height="6.6" rx="2" />
      <rect x="13.2" y="4.2" width="6.6" height="6.6" rx="2" />
      <rect x="4.2" y="13.2" width="6.6" height="6.6" rx="2" />
      <rect x="13.2" y="13.2" width="6.6" height="6.6" rx="2" fill="currentColor" stroke="none" />
    </Base>
  );
}

function HouseIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M4.5 10.3 12 4.2l7.5 6.1V19a1.4 1.4 0 0 1-1.4 1.4H5.9A1.4 1.4 0 0 1 4.5 19Z" />
      <path d="M4.5 10.3 12 4.2l7.5 6.1V19a1.4 1.4 0 0 1-1.4 1.4H5.9A1.4 1.4 0 0 1 4.5 19Z" />
      <path d="M9.9 20.2v-4a2.1 2.1 0 0 1 4.2 0v4" />
    </Base>
  );
}

function KeyIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M12.5 8.6a3.9 3.9 0 1 1-7.8 0 3.9 3.9 0 0 1 7.8 0Z" />
      <circle cx="8.6" cy="8.6" r="3.9" />
      <circle cx="8.6" cy="8.6" r=".9" fill="currentColor" stroke="none" />
      <path d="m11.4 11.4 8.2 8.2" />
      <path d="m15.3 15.3 2-2" />
      <path d="m18.1 18.1 2-2" />
    </Base>
  );
}

function CoinIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z" />
      <circle cx="12" cy="12" r="8" />
      <path d="m8.4 9.4 1.2 5.2 2.4-4.6 2.4 4.6 1.2-5.2" strokeWidth="1.5" />
      <path d="M7.7 11.9h8.6" strokeWidth="1.5" />
    </Base>
  );
}

function ReceiptIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M6.8 3.6h10.4v14.8l-2.08-1.5-2.08 1.5-2.08-1.5-2.08 1.5-2.08-1.5Z" />
      <path d="M6.8 18.4V3.6h10.4v14.8l-2.08-1.5-2.08 1.5-2.08-1.5-2.08 1.5-2.08-1.5Z" />
      <path d="M9.6 8.1h4.8M9.6 11.4h4.8" />
    </Base>
  );
}

function CraneIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M5.2 11h6.6v9.2H5.2Z" />
      <path d="M5.2 20.2V11h6.6v9.2" />
      <path d="M3.9 20.2h16.2" />
      <path d="M7.2 14.1h2.6M7.2 17h2.6" />
      <path d="M8.5 11V5.3h10.7" />
      <path d="M16.7 5.3v3" />
      <circle cx="16.7" cy="9.2" r=".9" />
    </Base>
  );
}

function RentIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M4.5 10.3 12 4.2l7.5 6.1V19a1.4 1.4 0 0 1-1.4 1.4H5.9A1.4 1.4 0 0 1 4.5 19Z" />
      <path d="M4.5 10.3 12 4.2l7.5 6.1V19a1.4 1.4 0 0 1-1.4 1.4H5.9A1.4 1.4 0 0 1 4.5 19Z" />
      <path d="M9.2 13.1h5.6l-1.6-1.6" />
      <path d="M14.8 16.3H9.2l1.6 1.6" />
    </Base>
  );
}

function BriefcaseIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M4.2 10a2.2 2.2 0 0 1 2.2-2.2h11.2A2.2 2.2 0 0 1 19.8 10v7a2.2 2.2 0 0 1-2.2 2.2H6.4A2.2 2.2 0 0 1 4.2 17Z" />
      <rect x="4.2" y="7.8" width="15.6" height="11.4" rx="2.2" />
      <path d="M9.7 7.8V6.3a2 2 0 0 1 2-2h.6a2 2 0 0 1 2 2v1.5" />
      <path d="M4.2 12.6h15.6" />
      <path d="M10.8 12.6v1.9h2.4v-1.9" />
    </Base>
  );
}

function HeartIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M12 18.9C9 16.9 5.6 14 5.6 10.4a3.6 3.6 0 0 1 6.4-2.2 3.6 3.6 0 0 1 6.4 2.2c0 3.6-3.4 6.5-6.4 8.5Z" />
      <path d="M12 18.9C9 16.9 5.6 14 5.6 10.4a3.6 3.6 0 0 1 6.4-2.2 3.6 3.6 0 0 1 6.4 2.2c0 3.6-3.4 6.5-6.4 8.5Z" />
      <path d="M18.4 4.2v2.4M17.2 5.4h2.4" strokeWidth="1.4" />
    </Base>
  );
}

function RocketIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M12 3.4c2.9 1.9 4.3 4.9 4.3 8a12 12 0 0 1-.9 4.5H8.6a12 12 0 0 1-.9-4.5c0-3.1 1.4-6.1 4.3-8Z" />
      <path d="M12 3.4c2.9 1.9 4.3 4.9 4.3 8a12 12 0 0 1-.9 4.5H8.6a12 12 0 0 1-.9-4.5c0-3.1 1.4-6.1 4.3-8Z" />
      <circle cx="12" cy="10.2" r="1.7" />
      <path d="M8.6 15.9 6.9 19.2l2.9-1.1M15.4 15.9l1.7 3.3-2.9-1.1" />
      <path d="M12 18.4v2.2" />
    </Base>
  );
}

function PramIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M4.6 10.2h14.8v.9a6 6 0 0 1-6 6h-2.8a6 6 0 0 1-6-6Z" />
      <Tone d="M4.6 10.2a7.6 7.6 0 0 1 7.6-7.6v7.6Z" />
      <path d="M4.6 10.2h14.8v.9a6 6 0 0 1-6 6h-2.8a6 6 0 0 1-6-6Z" />
      <path d="M4.6 10.2a7.6 7.6 0 0 1 7.6-7.6v7.6" />
      <circle cx="8.3" cy="19.7" r="1.4" />
      <circle cx="15.7" cy="19.7" r="1.4" />
    </Base>
  );
}

function GradCapIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M12 4.3 21.2 8.4 12 12.5 2.8 8.4Z" />
      <path d="M12 4.3 21.2 8.4 12 12.5 2.8 8.4Z" />
      <path d="M6.6 10.7v3.6c0 1.3 2.4 2.7 5.4 2.7s5.4-1.4 5.4-2.7v-3.6" />
      <path d="M21.2 8.4v4.6" />
      <circle cx="21.2" cy="14.2" r=".8" fill="currentColor" stroke="none" />
    </Base>
  );
}

function ChartIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M4.2 17.6l4.6-4.6 3.4 2.9 7.6-7.3v10.8H4.2Z" />
      <path d="m4.2 17.6 4.6-4.6 3.4 2.9 7.6-7.3" />
      <path d="M15.9 8.2h3.9v3.9" />
    </Base>
  );
}

/** 주관부처(정부기관) — 페디먼트 + 기둥. */
export function BuildingIcon(p: IconProps) {
  return (
    <Base {...p}>
      <Tone d="M12 3.6 20.2 8.4H3.8Z" />
      <path d="M12 3.6 20.2 8.4H3.8Z" />
      <path d="M6.6 11v6.2M12 11v6.2M17.4 11v6.2" />
      <path d="M4.4 19.8h15.2" />
    </Base>
  );
}

const CATEGORY_ICONS: Record<string, (p: IconProps) => React.ReactElement> = {
  전체: AllIcon,
  부동산: HouseIcon,
  청약: KeyIcon,
  대출: CoinIcon,
  세금: ReceiptIcon,
  재개발: CraneIcon,
  전월세: RentIcon,
  고용: BriefcaseIcon,
  복지: HeartIcon,
  창업: RocketIcon,
  육아: PramIcon,
  교육: GradCapIcon,
  금융: ChartIcon,
};

export function CategoryIcon({ cat, ...p }: IconProps & { cat: string }) {
  const Icon = CATEGORY_ICONS[cat] ?? AllIcon;
  return <Icon {...p} />;
}

/* ================= UI 아이콘 ================= */

export function SearchIcon(p: IconProps) {
  return (
    <Base strokeWidth={1.9} {...p}>
      <circle cx="11" cy="11" r="6.4" />
      <path d="m19.6 19.6-3.3-3.3" />
    </Base>
  );
}

export function ChevronDownIcon(p: IconProps) {
  return (
    <Base strokeWidth={1.9} {...p}>
      <path d="m6.5 9.5 5.5 5.5 5.5-5.5" />
    </Base>
  );
}

export function BookmarkIcon({ filled = false, ...p }: IconProps & { filled?: boolean }) {
  return (
    <Base {...p}>
      <path
        d="M7 4.4h10a1.1 1.1 0 0 1 1.1 1.1V20L12 16.1 5.9 20V5.5A1.1 1.1 0 0 1 7 4.4Z"
        fill={filled ? "currentColor" : "none"}
      />
    </Base>
  );
}

export function ShareIcon(p: IconProps) {
  return (
    <Base {...p}>
      <path d="M12 13.6V4.6" />
      <path d="M8.6 7.7 12 4.3l3.4 3.4" />
      <path d="M8.2 10.6H7a1.9 1.9 0 0 0-1.9 1.9v5.6A1.9 1.9 0 0 0 7 20h10a1.9 1.9 0 0 0 1.9-1.9v-5.6A1.9 1.9 0 0 0 17 10.6h-1.2" />
    </Base>
  );
}

export function ExternalLinkIcon(p: IconProps) {
  return (
    <Base {...p}>
      <path d="M18.9 13.2V18a1.9 1.9 0 0 1-1.9 1.9H6A1.9 1.9 0 0 1 4.1 18V7A1.9 1.9 0 0 1 6 5.1h4.8" />
      <path d="M14.2 4.4h5.4v5.4" />
      <path d="M19.4 4.6 12 12" />
    </Base>
  );
}

export function DownloadIcon(p: IconProps) {
  return (
    <Base {...p}>
      <path d="M12 4.2v10" />
      <path d="M7.6 10.4 12 14.8l4.4-4.4" />
      <path d="M5 19.6h14" />
    </Base>
  );
}

export function CheckIcon(p: IconProps) {
  return (
    <Base strokeWidth={2} {...p}>
      <path d="m5.4 12.6 4.3 4.3L18.6 7.4" />
    </Base>
  );
}

export function HomeIcon(p: IconProps) {
  return (
    <Base {...p}>
      <path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-4.5v-5.5h-5V21H5a1 1 0 0 1-1-1v-9.5Z" />
    </Base>
  );
}

export function BellIcon(p: IconProps) {
  return (
    <Base {...p}>
      <path d="M12 4a5.5 5.5 0 0 0-5.5 5.5v3.6L5 16.2a.8.8 0 0 0 .7 1.2h12.6a.8.8 0 0 0 .7-1.2l-1.5-3.1V9.5A5.5 5.5 0 0 0 12 4Z" />
      <path d="M10 19.8a2.2 2.2 0 0 0 4 0" />
    </Base>
  );
}

export function GearIcon(p: IconProps) {
  return (
    <Base {...p}>
      <circle cx="12" cy="12" r="3.2" />
      <path d="M19 12c0-.5.6-1.6.4-2l-1.2-2.1c-.2-.4-1.4-.3-1.9-.5-.4-.3-.9-1.4-1.4-1.5L12.5 5.5h-1L9.1 5.9c-.5.1-1 1.2-1.4 1.5-.5.2-1.7.1-1.9.5L4.6 10c-.2.4.4 1.5.4 2s-.6 1.6-.4 2l1.2 2.1c.2.4 1.4.3 1.9.5.4.3.9 1.4 1.4 1.5l2.4.4h1l2.4-.4c.5-.1 1-1.2 1.4-1.5.5-.2 1.7-.1 1.9-.5l1.2-2.1c.2-.4-.4-1.5-.4-2Z" />
    </Base>
  );
}
