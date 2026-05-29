# Policy Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GitHub Actions가 매일 2회 정부 사이트를 크롤링하고, HWP/PDF 문서를 파싱해 Claude Haiku로 구조화 요약을 생성한 뒤 GitHub Pages CDN에 배포하고 FCM 푸시 알림을 발송하는 파이프라인 구축

**Architecture:** Python 파이프라인은 `pipeline/` 디렉터리에 위치하며 단계별 모듈(crawler → extractor → summarizer → publisher → notifier)로 분리된다. JSON 결과물은 `docs/` 폴더에 커밋되어 GitHub Pages가 CDN으로 서빙한다. Firebase Cloud Function(Node.js)이 Firestore onWrite를 감지해 FCM multicast를 발송한다.

**Tech Stack:** Python 3.11, requests, beautifulsoup4, hwp5, pymupdf, anthropic, firebase-admin / Node.js 20, firebase-functions, firebase-admin (Cloud Function) / GitHub Actions, GitHub Pages

**⚠️ 선행 조건 (코딩 전 완료):**
1. GitHub 저장소 생성, GitHub Pages를 `docs/` 폴더 서빙으로 설정
2. Firebase 프로젝트 생성, Firestore 활성화, 서비스 계정 JSON 다운로드
3. GitHub Secrets 등록: `ANTHROPIC_API_KEY`, `FIREBASE_SERVICE_ACCOUNT` (서비스 계정 JSON을 문자열로)

---

## 파일 구조

```
pipeline/
  requirements.txt
  seen_policies.json          # 처리 완료된 정책 URL 해시 목록 (repo에 커밋)
  models.py                   # PolicyItem, PolicySummary 데이터클래스
  extractor.py                # HWP/HWPX/PDF/HTML 텍스트 추출
  summarizer.py               # Claude Haiku API 호출, 구조화 요약 반환
  crawler.py                  # 사이트별 크롤러 + 레지스트리
  publisher.py                # JSON 파일 생성, docs/ 에 저장
  notifier.py                 # Firestore new_policies 컬렉션에 기록
  main.py                     # 파이프라인 오케스트레이터
  tests/
    test_extractor.py
    test_summarizer.py
    test_publisher.py
    test_crawler.py

docs/
  policies/
    index.json                # 전체 최근 50건 목록
    {id}.json                 # 개별 정책 상세
  categories/
    {subcategory}/
      index.json              # 카테고리별 목록

functions/
  package.json
  index.js                    # Firebase Cloud Function

.github/
  workflows/
    pipeline.yml
```

---

### Task 1: 프로젝트 스캐폴딩 및 데이터 모델

**Files:**
- Create: `pipeline/requirements.txt`
- Create: `pipeline/seen_policies.json`
- Create: `pipeline/models.py`
- Create: `pipeline/__init__.py`
- Create: `pipeline/tests/__init__.py`
- Create: `docs/policies/.gitkeep`
- Create: `docs/categories/.gitkeep`

- [ ] **Step 1: `pipeline/__init__.py` 생성 (빈 파일)**

```python
# pipeline/__init__.py
```

- [ ] **Step 2: requirements.txt 작성**

```
requests==2.32.3
beautifulsoup4==4.12.3
lxml==5.3.0
hwp5==0.1.0
pymupdf==1.25.1
anthropic==0.40.0
firebase-admin==6.6.0
```

- [ ] **Step 2: 빈 seen_policies.json 생성**

```json
{"seen": []}
```

- [ ] **Step 3: models.py 작성 — 테스트 먼저**

`pipeline/tests/test_models.py`:
```python
from pipeline.models import PolicyItem, PolicySummary, RawPolicy

def test_policy_item_has_required_fields():
    item = PolicyItem(
        id="molit-2026-05-29-001",
        category="부동산",
        subcategory="청약",
        title="청약 제도 개편",
        source="국토교통부",
        source_url="https://example.com",
        file_url=None,
        file_type=None,
        published_at="2026-05-29T09:00:00+09:00",
    )
    assert item.id == "molit-2026-05-29-001"
    assert item.file_url is None

def test_raw_policy_url_hash():
    raw = RawPolicy(url="https://example.com/policy", title="제목", source="국토교통부",
                    published_at="2026-05-29", file_url=None, file_type=None, html_content="내용")
    assert len(raw.url_hash) == 64  # sha256 hex
```

- [ ] **Step 4: 테스트 실행 → 실패 확인**

```bash
cd D:\policy-alerm
python -m pytest pipeline/tests/test_models.py -v
```

Expected: `ImportError` (모듈 없음)

- [ ] **Step 5: models.py 구현**

`pipeline/models.py`:
```python
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
```

- [ ] **Step 6: 테스트 재실행 → 통과 확인**

```bash
python -m pytest pipeline/tests/test_models.py -v
```

Expected: PASSED (2 tests)

- [ ] **Step 7: 커밋**

```bash
git add pipeline/ docs/ .gitignore
git commit -m "feat: project scaffolding and data models"
```

---

### Task 2: 텍스트 추출기 (extractor.py)

**Files:**
- Create: `pipeline/extractor.py`
- Create: `pipeline/tests/test_extractor.py`

- [ ] **Step 1: 테스트 작성**

`pipeline/tests/test_extractor.py`:
```python
import zipfile
import io
import xml.etree.ElementTree as ET
from pipeline.extractor import extract_text, _extract_hwpx, _extract_pdf, _extract_html

def _make_hwpx_bytes() -> bytes:
    """최소 HWPX 구조를 가진 zip 파일 생성"""
    buf = io.BytesIO()
    ns = "http://www.hancom.co.kr/hwpml/2012/paragraph"
    xml_content = f"""<?xml version="1.0"?>
<BodyText xmlns:hp="{ns}">
  <hp:P><hp:Run><hp:T>청약 제도가 바뀝니다</hp:T></hp:Run></hp:P>
</BodyText>"""
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("Contents/section0.xml", xml_content)
    return buf.getvalue()

def test_extract_hwpx():
    content = _make_hwpx_bytes()
    text = _extract_hwpx(content)
    assert "청약 제도가 바뀝니다" in text

def test_extract_html():
    html = "<html><body><div class='content'><p>정책 내용입니다</p></div></body></html>"
    text = _extract_html(html)
    assert "정책 내용입니다" in text

def test_extract_text_dispatches_by_type(tmp_path):
    hwpx_bytes = _make_hwpx_bytes()
    result = extract_text(hwpx_bytes, "hwpx")
    assert len(result) > 0

def test_extract_text_falls_back_on_unknown_type():
    result = extract_text(b"", "unknown")
    assert result == ""
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
python -m pytest pipeline/tests/test_extractor.py -v
```

Expected: FAILED (ImportError)

- [ ] **Step 3: extractor.py 구현**

`pipeline/extractor.py`:
```python
import io
import os
import subprocess
import tempfile
import zipfile
import xml.etree.ElementTree as ET
from typing import Optional

from bs4 import BeautifulSoup


def extract_text(file_content: bytes, file_type: str) -> str:
    """파일 타입에 따라 텍스트를 추출한다. 실패 시 빈 문자열 반환."""
    try:
        if file_type == "hwpx":
            return _extract_hwpx(file_content)
        elif file_type == "hwp":
            return _extract_hwp(file_content)
        elif file_type == "pdf":
            return _extract_pdf(file_content)
        elif file_type == "html":
            return _extract_html(file_content.decode("utf-8", errors="ignore"))
        else:
            return ""
    except Exception:
        return ""


def _extract_hwpx(content: bytes) -> str:
    """HWPX(ZIP 기반 XML)에서 텍스트 추출"""
    texts = []
    with zipfile.ZipFile(io.BytesIO(content)) as z:
        section_files = [n for n in z.namelist() if n.startswith("Contents/section")]
        for section_file in sorted(section_files):
            with z.open(section_file) as f:
                root = ET.parse(f).getroot()
                for elem in root.iter():
                    if elem.text and elem.text.strip():
                        texts.append(elem.text.strip())
    return "\n".join(texts)


def _extract_hwp(content: bytes) -> str:
    """HWP 바이너리에서 텍스트 추출 (hwp5txt CLI 사용)"""
    with tempfile.NamedTemporaryFile(suffix=".hwp", delete=False) as f:
        f.write(content)
        tmp_path = f.name
    try:
        result = subprocess.run(
            ["hwp5txt", tmp_path],
            capture_output=True, text=True, timeout=30
        )
        return result.stdout
    finally:
        os.unlink(tmp_path)


def _extract_pdf(content: bytes) -> str:
    """PDF에서 텍스트 추출"""
    import pymupdf
    doc = pymupdf.open(stream=content, filetype="pdf")
    return "\n".join(page.get_text() for page in doc)


def _extract_html(html: str) -> str:
    """HTML 본문에서 텍스트 추출"""
    soup = BeautifulSoup(html, "lxml")
    for tag in soup(["script", "style", "nav", "footer", "header"]):
        tag.decompose()
    return soup.get_text(separator="\n", strip=True)
```

- [ ] **Step 4: 테스트 재실행 → 통과 확인**

```bash
python -m pytest pipeline/tests/test_extractor.py -v
```

Expected: PASSED (4 tests)

- [ ] **Step 5: 커밋**

```bash
git add pipeline/extractor.py pipeline/tests/test_extractor.py
git commit -m "feat: text extractor for HWP/HWPX/PDF/HTML"
```

---

### Task 3: AI 요약기 (summarizer.py)

**Files:**
- Create: `pipeline/summarizer.py`
- Create: `pipeline/tests/test_summarizer.py`

- [ ] **Step 1: 테스트 작성 (mock 사용)**

`pipeline/tests/test_summarizer.py`:
```python
from unittest.mock import MagicMock, patch
from pipeline.summarizer import summarize_policy
from pipeline.models import PolicySummary

MOCK_RESPONSE_JSON = """{
  "what_changed": "청약 가점 우대 폭 확대",
  "who_is_affected": "무주택 기간 3년 이상 세대주",
  "when_effective": "2026년 7월 1일부터",
  "key_points": ["가점제 비율 상향", "특별공급 소득 기준 완화"]
}"""

def test_summarize_policy_returns_policy_summary():
    mock_client = MagicMock()
    mock_client.messages.create.return_value.content = [
        MagicMock(text=MOCK_RESPONSE_JSON)
    ]
    result = summarize_policy("청약 제도 개편", "긴 정책 내용...", client=mock_client)
    assert isinstance(result, PolicySummary)
    assert result.what_changed == "청약 가점 우대 폭 확대"
    assert len(result.key_points) == 2

def test_summarize_policy_truncates_long_text():
    mock_client = MagicMock()
    mock_client.messages.create.return_value.content = [
        MagicMock(text=MOCK_RESPONSE_JSON)
    ]
    long_text = "가" * 20000
    summarize_policy("제목", long_text, client=mock_client)
    call_args = mock_client.messages.create.call_args
    prompt = call_args.kwargs["messages"][0]["content"]
    assert len(prompt) < 12000  # 텍스트가 잘렸는지 확인
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
python -m pytest pipeline/tests/test_summarizer.py -v
```

Expected: FAILED

- [ ] **Step 3: summarizer.py 구현**

`pipeline/summarizer.py`:
```python
import json
from typing import Optional
import anthropic
from pipeline.models import PolicySummary

_PROMPT_TEMPLATE = """다음 정책 문서를 분석하여 JSON 형식으로만 응답하세요. 설명 없이 JSON만 출력하세요.

제목: {title}

내용:
{text}

응답 형식:
{{
  "what_changed": "무엇이 바뀌었는지 1-2문장 (구체적 수치 포함)",
  "who_is_affected": "누가 대상인지 1-2문장",
  "when_effective": "언제부터 적용되는지",
  "key_points": ["핵심 포인트 1", "핵심 포인트 2", "핵심 포인트 3"]
}}"""


def summarize_policy(
    title: str,
    text: str,
    client: Optional[anthropic.Anthropic] = None,
) -> PolicySummary:
    if client is None:
        client = anthropic.Anthropic()

    truncated = text[:8000]
    prompt = _PROMPT_TEMPLATE.format(title=title, text=truncated)

    message = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=1024,
        messages=[{"role": "user", "content": prompt}],
    )

    raw = json.loads(message.content[0].text)
    return PolicySummary(
        what_changed=raw["what_changed"],
        who_is_affected=raw["who_is_affected"],
        when_effective=raw["when_effective"],
        key_points=raw["key_points"],
    )
```

- [ ] **Step 4: 테스트 재실행 → 통과 확인**

```bash
python -m pytest pipeline/tests/test_summarizer.py -v
```

Expected: PASSED (2 tests)

- [ ] **Step 5: 커밋**

```bash
git add pipeline/summarizer.py pipeline/tests/test_summarizer.py
git commit -m "feat: Claude Haiku policy summarizer"
```

---

### Task 4: 크롤러 (crawler.py)

**Files:**
- Create: `pipeline/crawler.py`
- Create: `pipeline/tests/test_crawler.py`

- [ ] **Step 1: 테스트 작성**

`pipeline/tests/test_crawler.py`:
```python
from unittest.mock import patch, MagicMock
from pipeline.crawler import MolitCrawler, load_seen, save_seen, is_new_policy

def test_load_seen_returns_set(tmp_path):
    seen_file = tmp_path / "seen.json"
    seen_file.write_text('{"seen": ["abc123", "def456"]}')
    result = load_seen(str(seen_file))
    assert result == {"abc123", "def456"}

def test_save_and_load_roundtrip(tmp_path):
    seen_file = tmp_path / "seen.json"
    seen_file.write_text('{"seen": []}')
    save_seen({"hash1", "hash2"}, str(seen_file))
    result = load_seen(str(seen_file))
    assert result == {"hash1", "hash2"}

def test_is_new_policy():
    seen = {"existing_hash"}
    assert is_new_policy("existing_hash", seen) is False
    assert is_new_policy("new_hash", seen) is True
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
python -m pytest pipeline/tests/test_crawler.py -v
```

Expected: FAILED

- [ ] **Step 3: crawler.py 구현**

`pipeline/crawler.py`:
```python
import json
import re
from dataclasses import dataclass
from datetime import datetime
from typing import Optional
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from pipeline.models import RawPolicy

SEEN_FILE = "pipeline/seen_policies.json"
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; PolicyBot/1.0)"}


def load_seen(path: str = SEEN_FILE) -> set[str]:
    with open(path, encoding="utf-8") as f:
        return set(json.load(f)["seen"])


def save_seen(seen: set[str], path: str = SEEN_FILE) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"seen": list(seen)}, f, ensure_ascii=False)


def is_new_policy(url_hash: str, seen: set[str]) -> bool:
    return url_hash not in seen


class MolitCrawler:
    """국토교통부 보도자료 크롤러"""

    BASE_URL = "https://www.molit.go.kr"
    LIST_URL = "https://www.molit.go.kr/USR/NEWS/m_71/dtl.jsp?id=95080455"
    CATEGORY = "부동산"
    SOURCE = "국토교통부"

    def fetch(self) -> list[RawPolicy]:
        resp = requests.get(self.LIST_URL, headers=HEADERS, timeout=20)
        resp.raise_for_status()
        soup = BeautifulSoup(resp.text, "lxml")

        results = []
        for row in soup.select("table.board_list tbody tr"):
            cols = row.select("td")
            if len(cols) < 4:
                continue

            link_tag = row.select_one("td.title a")
            if not link_tag:
                continue

            title = link_tag.get_text(strip=True)
            href = link_tag.get("href", "")
            url = urljoin(self.BASE_URL, href)
            date_text = cols[-1].get_text(strip=True)

            detail = self._fetch_detail(url)
            raw = RawPolicy(
                url=url,
                title=title,
                source=self.SOURCE,
                published_at=self._parse_date(date_text),
                file_url=detail.get("file_url"),
                file_type=detail.get("file_type"),
                html_content=detail.get("html_content", ""),
            )
            results.append(raw)

        return results

    def _fetch_detail(self, url: str) -> dict:
        try:
            resp = requests.get(url, headers=HEADERS, timeout=20)
            resp.raise_for_status()
            soup = BeautifulSoup(resp.text, "lxml")

            file_url = None
            file_type = None
            for a in soup.select("a[href]"):
                href = a["href"]
                for ext in ("hwpx", "hwp", "pdf"):
                    if href.lower().endswith(f".{ext}"):
                        file_url = urljoin(url, href)
                        file_type = ext
                        break
                if file_url:
                    break

            content_div = soup.select_one(".board_view") or soup.select_one(".content")
            html_content = content_div.get_text(separator="\n", strip=True) if content_div else ""

            return {"file_url": file_url, "file_type": file_type, "html_content": html_content}
        except Exception:
            return {"file_url": None, "file_type": None, "html_content": ""}

    @staticmethod
    def _parse_date(date_text: str) -> str:
        date_text = re.sub(r"[^\d\-\.]", "", date_text)
        try:
            dt = datetime.strptime(date_text, "%Y.%m.%d")
            return dt.strftime("%Y-%m-%dT00:00:00+09:00")
        except ValueError:
            return datetime.now().strftime("%Y-%m-%dT00:00:00+09:00")


class KoreaPolicyBriefingCrawler:
    """정책브리핑 부동산 섹션 크롤러"""

    BASE_URL = "https://www.korea.kr"
    LIST_URL = "https://www.korea.kr/news/pressReleaseView.do?newsId=&srchType=R&policyType=&subId=&pageIndex=1&srchWord=부동산"
    CATEGORY = "부동산"
    SOURCE = "정책브리핑"

    def fetch(self) -> list[RawPolicy]:
        try:
            resp = requests.get(self.LIST_URL, headers=HEADERS, timeout=20)
            resp.raise_for_status()
            soup = BeautifulSoup(resp.text, "lxml")
            results = []
            for item in soup.select(".news_list li")[:10]:
                link_tag = item.select_one("a")
                if not link_tag:
                    continue
                title = item.select_one(".title")
                date_tag = item.select_one(".date")
                if not title or not date_tag:
                    continue
                url = urljoin(self.BASE_URL, link_tag.get("href", ""))
                results.append(RawPolicy(
                    url=url,
                    title=title.get_text(strip=True),
                    source=self.SOURCE,
                    published_at=self._parse_date(date_tag.get_text(strip=True)),
                    file_url=None,
                    file_type=None,
                    html_content="",
                ))
            return results
        except Exception:
            return []

    @staticmethod
    def _parse_date(date_text: str) -> str:
        try:
            dt = datetime.strptime(date_text.strip(), "%Y.%m.%d")
            return dt.strftime("%Y-%m-%dT00:00:00+09:00")
        except ValueError:
            return datetime.now().strftime("%Y-%m-%dT00:00:00+09:00")


# 크롤러 레지스트리 — 새 사이트 추가 시 여기에만 등록
CRAWLERS = [
    MolitCrawler(),
    KoreaPolicyBriefingCrawler(),
]
```

- [ ] **Step 4: 테스트 재실행 → 통과 확인**

```bash
python -m pytest pipeline/tests/test_crawler.py -v
```

Expected: PASSED (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add pipeline/crawler.py pipeline/tests/test_crawler.py
git commit -m "feat: government site crawlers (molit, korea.kr)"
```

---

### Task 5: 퍼블리셔 (publisher.py)

**Files:**
- Create: `pipeline/publisher.py`
- Create: `pipeline/tests/test_publisher.py`

- [ ] **Step 1: 테스트 작성**

`pipeline/tests/test_publisher.py`:
```python
import json
import os
from pathlib import Path
from pipeline.publisher import publish_policy, update_index, build_policy_id
from pipeline.models import PolicyItem, PolicySummary

def _make_item() -> PolicyItem:
    return PolicyItem(
        id="molit-2026-05-29-001",
        category="부동산",
        subcategory="청약",
        title="청약 제도 개편",
        source="국토교통부",
        source_url="https://example.com",
        file_url=None,
        file_type=None,
        published_at="2026-05-29T09:00:00+09:00",
        crawled_at="2026-05-29T09:10:00+09:00",
        summary=PolicySummary(
            what_changed="가점 확대",
            who_is_affected="무주택자",
            when_effective="7월부터",
            key_points=["포인트1", "포인트2"]
        )
    )

def test_build_policy_id():
    pid = build_policy_id("국토교통부", "2026-05-29T09:00:00+09:00", "https://example.com/123")
    assert pid.startswith("국토교통부-2026-05-29-")
    assert len(pid) > 15

def test_publish_policy_creates_file(tmp_path):
    item = _make_item()
    publish_policy(item, docs_root=str(tmp_path))
    path = tmp_path / "policies" / "molit-2026-05-29-001.json"
    assert path.exists()
    data = json.loads(path.read_text(encoding="utf-8"))
    assert data["title"] == "청약 제도 개편"
    assert data["summary"]["what_changed"] == "가점 확대"

def test_update_index_prepends_item(tmp_path):
    item = _make_item()
    publish_policy(item, docs_root=str(tmp_path))
    update_index(item, docs_root=str(tmp_path))
    index = json.loads((tmp_path / "policies" / "index.json").read_text())
    assert len(index["items"]) == 1
    assert index["items"][0]["id"] == "molit-2026-05-29-001"

def test_update_index_keeps_max_50(tmp_path):
    for i in range(55):
        item = _make_item()
        item.id = f"policy-{i:03d}"
        publish_policy(item, docs_root=str(tmp_path))
        update_index(item, docs_root=str(tmp_path))
    index = json.loads((tmp_path / "policies" / "index.json").read_text())
    assert len(index["items"]) == 50
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
python -m pytest pipeline/tests/test_publisher.py -v
```

Expected: FAILED

- [ ] **Step 3: publisher.py 구현**

`pipeline/publisher.py`:
```python
import hashlib
import json
import os
from dataclasses import asdict
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Optional

from pipeline.models import PolicyItem, PolicySummary

DOCS_ROOT = "docs"
KST = timezone(timedelta(hours=9))


def build_policy_id(source: str, published_at: str, url: str) -> str:
    date_part = published_at[:10]
    url_short = hashlib.md5(url.encode()).hexdigest()[:8]
    safe_source = source.replace(" ", "_")[:10]
    return f"{safe_source}-{date_part}-{url_short}"


def publish_policy(item: PolicyItem, docs_root: str = DOCS_ROOT) -> None:
    policies_dir = Path(docs_root) / "policies"
    policies_dir.mkdir(parents=True, exist_ok=True)

    data = {
        "id": item.id,
        "category": item.category,
        "subcategory": item.subcategory,
        "title": item.title,
        "source": item.source,
        "source_url": item.source_url,
        "file_url": item.file_url,
        "file_type": item.file_type,
        "published_at": item.published_at,
        "crawled_at": item.crawled_at,
        "summary": {
            "what_changed": item.summary.what_changed,
            "who_is_affected": item.summary.who_is_affected,
            "when_effective": item.summary.when_effective,
            "key_points": item.summary.key_points,
        } if item.summary else None,
    }

    out_path = policies_dir / f"{item.id}.json"
    out_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    _update_category_index(item, docs_root)


def update_index(item: PolicyItem, docs_root: str = DOCS_ROOT) -> None:
    index_path = Path(docs_root) / "policies" / "index.json"
    if index_path.exists():
        index = json.loads(index_path.read_text(encoding="utf-8"))
    else:
        index = {"items": [], "total": 0, "updated_at": ""}

    entry = {
        "id": item.id,
        "category": item.category,
        "subcategory": item.subcategory,
        "title": item.title,
        "source": item.source,
        "published_at": item.published_at,
        "summary_preview": (item.summary.what_changed[:100] + "...") if item.summary else "",
    }

    index["items"] = [entry] + [i for i in index["items"] if i["id"] != item.id]
    index["items"] = index["items"][:50]
    index["total"] = len(index["items"])
    index["updated_at"] = datetime.now(KST).isoformat()

    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8")


def _update_category_index(item: PolicyItem, docs_root: str) -> None:
    cat_dir = Path(docs_root) / "categories" / item.subcategory
    cat_dir.mkdir(parents=True, exist_ok=True)
    index_path = cat_dir / "index.json"

    if index_path.exists():
        index = json.loads(index_path.read_text(encoding="utf-8"))
    else:
        index = {"subcategory": item.subcategory, "items": []}

    entry = {"id": item.id, "title": item.title, "published_at": item.published_at}
    index["items"] = [entry] + [i for i in index["items"] if i["id"] != item.id]
    index["items"] = index["items"][:30]

    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8")
```

- [ ] **Step 4: 테스트 재실행 → 통과 확인**

```bash
python -m pytest pipeline/tests/test_publisher.py -v
```

Expected: PASSED (4 tests)

- [ ] **Step 5: 커밋**

```bash
git add pipeline/publisher.py pipeline/tests/test_publisher.py
git commit -m "feat: JSON publisher for GitHub Pages CDN"
```

---

### Task 6: Firestore 노티파이어 (notifier.py)

**Files:**
- Create: `pipeline/notifier.py`
- Create: `pipeline/tests/test_notifier.py`

- [ ] **Step 1: 테스트 작성**

`pipeline/tests/test_notifier.py`:
```python
from unittest.mock import MagicMock, patch
from pipeline.notifier import notify_new_policy
from pipeline.models import PolicyItem, PolicySummary

def _make_item() -> PolicyItem:
    return PolicyItem(
        id="molit-2026-05-29-001",
        category="부동산", subcategory="청약",
        title="청약 제도 개편", source="국토교통부",
        source_url="https://example.com",
        file_url=None, file_type=None,
        published_at="2026-05-29T09:00:00+09:00",
        summary=PolicySummary("변경사항", "대상자", "7월부터", ["포인트"])
    )

def test_notify_new_policy_writes_to_firestore():
    mock_db = MagicMock()
    mock_doc_ref = MagicMock()
    mock_db.collection.return_value.document.return_value = mock_doc_ref

    notify_new_policy(_make_item(), batch="morning", db=mock_db)

    mock_db.collection.assert_called_with("new_policies")
    mock_db.collection.return_value.document.assert_called_with("molit-2026-05-29-001")
    mock_doc_ref.set.assert_called_once()
    call_data = mock_doc_ref.set.call_args[0][0]
    assert call_data["batch"] == "morning"
    assert call_data["subcategory"] == "청약"
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
python -m pytest pipeline/tests/test_notifier.py -v
```

Expected: FAILED

- [ ] **Step 3: notifier.py 구현**

`pipeline/notifier.py`:
```python
import json
import os
from typing import Optional

import firebase_admin
from firebase_admin import credentials, firestore

from pipeline.models import PolicyItem


def _get_db(db=None):
    if db is not None:
        return db
    if not firebase_admin._apps:
        service_account = json.loads(os.environ["FIREBASE_SERVICE_ACCOUNT"])
        cred = credentials.Certificate(service_account)
        firebase_admin.initialize_app(cred)
    return firestore.client()


def notify_new_policy(item: PolicyItem, batch: str, db=None) -> None:
    """Firestore new_policies/{id} 에 문서를 생성해 Cloud Function을 트리거한다."""
    client = _get_db(db)
    client.collection("new_policies").document(item.id).set({
        "category": item.category,
        "subcategory": item.subcategory,
        "title": item.title,
        "batch": batch,  # "morning" | "evening"
    })
```

- [ ] **Step 4: 테스트 재실행 → 통과 확인**

```bash
python -m pytest pipeline/tests/test_notifier.py -v
```

Expected: PASSED (1 test)

- [ ] **Step 5: 커밋**

```bash
git add pipeline/notifier.py pipeline/tests/test_notifier.py
git commit -m "feat: Firestore notifier for FCM trigger"
```

---

### Task 7: 메인 오케스트레이터 (main.py)

**Files:**
- Create: `pipeline/main.py`

- [ ] **Step 1: main.py 작성**

`pipeline/main.py`:
```python
import os
import sys
import requests
from datetime import datetime, timezone, timedelta

from pipeline.crawler import CRAWLERS, load_seen, save_seen, is_new_policy
from pipeline.extractor import extract_text
from pipeline.summarizer import summarize_policy
from pipeline.publisher import build_policy_id, publish_policy, update_index
from pipeline.notifier import notify_new_policy
from pipeline.models import PolicyItem

KST = timezone(timedelta(hours=9))
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; PolicyBot/1.0)"}


def download_file(url: str) -> bytes:
    resp = requests.get(url, headers=HEADERS, timeout=30, stream=True)
    resp.raise_for_status()
    return resp.content


def run(batch: str) -> None:
    seen = load_seen()
    new_items = []

    for crawler in CRAWLERS:
        print(f"[{crawler.SOURCE}] 크롤링 시작")
        try:
            raw_policies = crawler.fetch()
        except Exception as e:
            print(f"[{crawler.SOURCE}] 크롤링 실패: {e}", file=sys.stderr)
            continue

        for raw in raw_policies:
            if not is_new_policy(raw.url_hash, seen):
                continue

            print(f"  신규 정책 발견: {raw.title}")

            # 텍스트 추출
            text = raw.html_content
            if raw.file_url and raw.file_type:
                try:
                    file_bytes = download_file(raw.file_url)
                    extracted = extract_text(file_bytes, raw.file_type)
                    if extracted.strip():
                        text = extracted
                except Exception as e:
                    print(f"  파일 추출 실패, HTML 폴백: {e}", file=sys.stderr)

            # AI 요약
            try:
                summary = summarize_policy(raw.title, text)
            except Exception as e:
                print(f"  요약 실패, 건너뜀: {e}", file=sys.stderr)
                seen.add(raw.url_hash)
                continue

            policy_id = build_policy_id(raw.source, raw.published_at, raw.url)
            item = PolicyItem(
                id=policy_id,
                category="부동산",
                subcategory=_classify_subcategory(raw.title),
                title=raw.title,
                source=raw.source,
                source_url=raw.url,
                file_url=raw.file_url,
                file_type=raw.file_type,
                published_at=raw.published_at,
                crawled_at=datetime.now(KST).isoformat(),
                summary=summary,
            )

            publish_policy(item)
            update_index(item)
            new_items.append((item, batch))
            seen.add(raw.url_hash)

    save_seen(seen)

    # 모든 JSON 커밋 후 Firestore 알림
    for item, b in new_items:
        try:
            notify_new_policy(item, batch=b)
            print(f"  FCM 트리거 완료: {item.title}")
        except Exception as e:
            print(f"  FCM 트리거 실패: {e}", file=sys.stderr)

    print(f"완료: {len(new_items)}건 처리됨")


def _classify_subcategory(title: str) -> str:
    if any(k in title for k in ["청약", "분양", "공급"]):
        return "청약"
    elif any(k in title for k in ["대출", "LTV", "DSR", "금리"]):
        return "대출"
    elif any(k in title for k in ["세금", "취득세", "종부세", "양도세"]):
        return "세금"
    elif any(k in title for k in ["재개발", "재건축", "정비"]):
        return "재개발"
    elif any(k in title for k in ["전세", "월세", "임대"]):
        return "전월세"
    return "부동산"


if __name__ == "__main__":
    batch = os.environ.get("BATCH", "morning")
    run(batch)
```

- [ ] **Step 2: 로컬에서 dry-run (실제 크롤링 없이 import 확인)**

```bash
python -c "from pipeline.main import run; print('import OK')"
```

Expected: `import OK`

- [ ] **Step 3: 전체 테스트 실행**

```bash
python -m pytest pipeline/tests/ -v
```

Expected: 모든 테스트 PASSED

- [ ] **Step 4: 커밋**

```bash
git add pipeline/main.py
git commit -m "feat: pipeline orchestrator"
```

---

### Task 8: GitHub Actions 워크플로우

**Files:**
- Create: `.github/workflows/pipeline.yml`

- [ ] **Step 1: pipeline.yml 작성**

`.github/workflows/pipeline.yml`:
```yaml
name: Policy Pipeline

on:
  schedule:
    - cron: '0 0 * * *'   # 오전 9시 KST (= 00:00 UTC)
    - cron: '0 9 * * *'   # 오후 6시 KST (= 09:00 UTC)
  workflow_dispatch:
    inputs:
      batch:
        description: 'morning or evening'
        default: 'morning'

permissions:
  contents: write

jobs:
  crawl-and-publish:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'

      - name: Install dependencies
        run: pip install -r pipeline/requirements.txt

      - name: Determine batch
        id: batch
        run: |
          if [ "${{ github.event_name }}" = "workflow_dispatch" ]; then
            echo "batch=${{ github.event.inputs.batch }}" >> $GITHUB_OUTPUT
          else
            HOUR_UTC=$(date -u +%H)
            if [ "$HOUR_UTC" = "00" ]; then
              echo "batch=morning" >> $GITHUB_OUTPUT
            else
              echo "batch=evening" >> $GITHUB_OUTPUT
            fi
          fi

      - name: Run pipeline
        run: python pipeline/main.py
        env:
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
          FIREBASE_SERVICE_ACCOUNT: ${{ secrets.FIREBASE_SERVICE_ACCOUNT }}
          BATCH: ${{ steps.batch.outputs.batch }}

      - name: Commit and push updated data
        run: |
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git config user.name "github-actions[bot]"
          git add docs/ pipeline/seen_policies.json
          git diff --staged --quiet || git commit -m "chore: update policy data [skip ci]"
          git push
```

- [ ] **Step 2: GitHub Secrets 등록 확인**

GitHub 저장소 → Settings → Secrets and variables → Actions에서 아래 두 항목 등록 여부 확인:
- `ANTHROPIC_API_KEY`: Anthropic Console에서 발급
- `FIREBASE_SERVICE_ACCOUNT`: Firebase Console → 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성 → JSON 전체를 문자열로 붙여넣기

- [ ] **Step 3: GitHub Pages 활성화 확인**

GitHub 저장소 → Settings → Pages → Source: "Deploy from a branch", Branch: `main`, Folder: `/docs`

- [ ] **Step 4: workflow_dispatch로 수동 실행 테스트**

GitHub Actions 탭 → Policy Pipeline → Run workflow → batch: morning → Run

정상 완료 후 `docs/policies/index.json`이 커밋되었는지 확인

- [ ] **Step 5: 커밋**

```bash
git add .github/
git commit -m "feat: GitHub Actions cron pipeline workflow"
```

---

### Task 9: Firebase Cloud Function (FCM 발송)

**Files:**
- Create: `functions/package.json`
- Create: `functions/index.js`

- [ ] **Step 1: package.json 작성**

`functions/package.json`:
```json
{
  "name": "policy-alarm-functions",
  "version": "1.0.0",
  "main": "index.js",
  "engines": { "node": "20" },
  "dependencies": {
    "firebase-admin": "^12.0.0",
    "firebase-functions": "^5.0.0"
  }
}
```

- [ ] **Step 2: index.js 작성**

`functions/index.js`:
```javascript
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.onNewPolicy = onDocumentCreated(
  "new_policies/{policyId}",
  async (event) => {
    const policy = event.data.data();
    const policyId = event.params.policyId;
    const db = getFirestore();

    // 구독 카테고리가 일치하는 유저 조회
    const usersSnap = await db
      .collection("users")
      .where("subscribed_categories", "array-contains", policy.subcategory)
      .get();

    const tokens = [];
    usersSnap.forEach((doc) => {
      const user = doc.data();
      const schedule = user.notification_schedule || "both";
      // 유저의 알림 시간 설정과 배치 매칭
      if (schedule === "both" || schedule === policy.batch) {
        if (user.fcm_token) {
          tokens.push(user.fcm_token);
        }
      }
    });

    // Firestore 트리거 문서 삭제 (중복 방지)
    await event.data.ref.delete();

    if (tokens.length === 0) {
      console.log(`No matching users for policy: ${policyId}`);
      return;
    }

    // FCM multicast 발송 (최대 500개씩)
    const chunkSize = 500;
    for (let i = 0; i < tokens.length; i += chunkSize) {
      const chunk = tokens.slice(i, i + chunkSize);
      const message = {
        notification: {
          title: `새 ${policy.category} 정책`,
          body: policy.title,
        },
        data: {
          policy_id: policyId,
          category: policy.category,
          subcategory: policy.subcategory,
        },
        tokens: chunk,
      };

      const response = await getMessaging().sendEachForMulticast(message);
      console.log(
        `Sent ${response.successCount}/${chunk.length} notifications for ${policyId}`
      );

      // 만료된 토큰 정리
      const expiredTokens = [];
      response.responses.forEach((resp, idx) => {
        if (
          !resp.success &&
          resp.error?.code === "messaging/registration-token-not-registered"
        ) {
          expiredTokens.push(chunk[idx]);
        }
      });

      if (expiredTokens.length > 0) {
        const batch = db.batch();
        const usersWithExpiredSnap = await db
          .collection("users")
          .where("fcm_token", "in", expiredTokens)
          .get();
        usersWithExpiredSnap.forEach((doc) => {
          batch.update(doc.ref, { fcm_token: null });
        });
        await batch.commit();
      }
    }
  }
);
```

- [ ] **Step 3: Firebase CLI로 배포**

```bash
npm install -g firebase-tools
firebase login
cd functions && npm install
cd ..
firebase deploy --only functions
```

Expected: `Deploy complete!`

- [ ] **Step 4: 커밋**

```bash
git add functions/
git commit -m "feat: Firebase Cloud Function for FCM multicast"
```

---

### Task 10: 파이프라인 전체 통합 테스트

- [ ] **Step 1: 모든 단위 테스트 실행**

```bash
python -m pytest pipeline/tests/ -v --tb=short
```

Expected: 전체 PASSED

- [ ] **Step 2: workflow_dispatch로 전체 파이프라인 실행**

GitHub Actions → Policy Pipeline → Run workflow

실행 완료 후 확인:
- `docs/policies/index.json` 파일이 저장소에 존재하는지
- GitHub Pages URL(`https://{username}.github.io/policy-alarm/policies/index.json`)에서 JSON이 응답하는지

- [ ] **Step 3: Firestore 콘솔에서 확인**

Firebase Console → Firestore → `new_policies` 컬렉션이 빈 상태인지 확인 (Cloud Function이 문서를 삭제했으면 정상)

- [ ] **Step 4: 최종 커밋**

```bash
git add .
git commit -m "chore: pipeline complete and tested"
```
