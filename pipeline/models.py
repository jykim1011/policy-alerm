import hashlib
from dataclasses import dataclass, field
from typing import Optional

@dataclass
class RawPolicy:
    url: str
    title: str
    source: str
    published_at: str
    file_url: Optional[str]
    file_type: Optional[str]  # "hwp" | "hwpx" | "pdf" | None
    html_content: str
    category: str = ""

    @property
    def url_hash(self) -> str:
        return hashlib.sha256(self.url.encode()).hexdigest()

@dataclass
class PolicySummary:
    what_changed: str
    who_is_affected: str
    when_effective: str
    key_points: list[str]
    # ↓ 시민 가치 보강 필드(선택). 본문에 근거가 없으면 빈 값으로 둔다(환각 방지).
    background: str = ""                      # 왜 이 정책이 나왔는지 맥락 1-2문장
    eligibility: list[str] = field(default_factory=list)   # "나에게 해당되나요?" 자가 체크
    how_to_apply: Optional[str] = None        # 신청 방법·창구·기간 (없으면 None)
    faq: list[dict] = field(default_factory=list)          # [{"question","answer"}]
    glossary: list[dict] = field(default_factory=list)     # [{"term","definition"}]

@dataclass
class PolicyItem:
    id: str
    category: str
    subcategory: str
    title: str
    source: str
    source_url: str
    file_url: Optional[str]
    file_type: Optional[str]
    published_at: str
    summary: Optional[PolicySummary] = None
    crawled_at: str = ""
