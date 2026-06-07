import hashlib
from dataclasses import dataclass
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
