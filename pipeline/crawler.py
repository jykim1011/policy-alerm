import html
import json
import os
import sys
from datetime import datetime, timezone, timedelta

import requests
from bs4 import BeautifulSoup

from pipeline.extractor import _clean_noise
from pipeline.models import RawPolicy

SEEN_FILE = "pipeline/seen_policies.json"
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; PolicyBot/1.0)"}
KST = timezone(timedelta(hours=9))

# 정책브리핑 정책뉴스 OpenAPI (공공데이터포털, 문화체육관광부 1371000).
# 정부 사이트 직접 크롤링은 해외/데이터센터 IP를 차단(Connection reset)하므로
# 차단되지 않는 apis.data.go.kr 게이트웨이를 사용한다.
# 2026-09 경 구 pressReleaseService(보도자료 API)가 폐기되어 후속 버전인
# policyNewsService2/policyNewsList2 로 교체됨. RSS(korea.kr/rss/pressReleaseList.do)는
# 2026-07-01부로 영구 종료(저작권 정책 변경)되어 대체 불가 — 폴백으로 쓰지 않는다.
POLICY_NEWS_API = "https://apis.data.go.kr/1371000/policyNewsService2/policyNewsList2"

# 카테고리별 매칭 키워드. 순서가 우선순위 — 창업은 복지보다 앞에 있어야
# "소상공인 지원금" 같은 중의적 제목이 창업으로 분류된다.
CATEGORY_KEYWORDS: dict[str, dict] = {
    "부동산": {
        # "분양"·"전세" 단독 키워드는 오탐이 잦아(식물 분양, 전세 버스 등)
        # 부동산 문맥이 확실한 복합어만 사용한다.
        "keywords": (
            "부동산", "주택", "청약", "월세", "임대주택", "임대차",
            "재개발", "재건축", "정비사업", "LTV", "DSR", "종부세", "양도세", "취득세",
            "주담대", "분양가", "분양권", "공공분양", "민간분양", "분양전환",
            "전세사기", "전세대출", "전세보증", "전세자금", "전셋값", "전세금",
            "전세시장", "역전세", "전세 계약", "전세계약", "안심전세",
            # 규제지역 지정·해제 발표는 제목에 주택·아파트가 없는 경우가 많다
            # (2026-06-30 동탄·기흥·구리 지정 미수집 장애).
            "조정대상지역", "투기과열", "토지거래허가", "규제지역",
            "주거정책심의", "주정심",
            "공시가격", "그린벨트", "신도시", "택지", "주거",
            "아파트", "주택담보", "다주택", "1주택", "보금자리", "공공주택",
        ),
        "exclude": ("인재개발",),
    },
    "고용": {
        "keywords": (
            "고용", "취업", "일자리", "실업급여", "채용", "고용보험",
            "고용지원", "청년취업", "취업지원", "구직",
        ),
        "exclude": (),
    },
    "창업": {
        "keywords": ("창업", "소상공인", "자영업", "중소기업", "폐업지원", "소기업"),
        "exclude": (),
    },
    "육아": {
        "keywords": (
            "출산", "육아", "보육", "아이돌봄", "어린이집", "임신", "산후",
            "육아휴직", "유아",
        ),
        "exclude": (),
    },
    "교육": {
        "keywords": ("장학금", "학자금", "교육비", "등록금", "학비", "교육지원"),
        "exclude": (),
    },
    "복지": {
        "keywords": (
            "복지", "지원금", "기초생활", "수당", "바우처", "의료급여",
            "장애인 지원", "사회보장", "생계급여",
        ),
        "exclude": (),
    },
    "금융": {
        "keywords": (
            "주식", "증권", "공매도", "금투세", "금융투자", "자본시장",
            "펀드", "ETF", "ISA", "IRP", "코스피", "코스닥",
            "배당소득세", "증권거래세", "기준금리", "상장",
        ),
        "exclude": (),
    },
}


def _classify_category(title: str) -> str | None:
    for category, cfg in CATEGORY_KEYWORDS.items():
        if any(bad in title for bad in cfg["exclude"]):
            continue
        if any(kw in title for kw in cfg["keywords"]):
            return category
    return None


def load_seen(path: str = SEEN_FILE) -> set[str]:
    with open(path, encoding="utf-8") as f:
        return set(json.load(f)["seen"])


def save_seen(seen: set[str], path: str = SEEN_FILE) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"seen": sorted(seen)}, f, ensure_ascii=False)


def is_new_policy(url_hash: str, seen: set[str]) -> bool:
    return url_hash not in seen


def _text(node, tag: str) -> str:
    el = node.find(tag)
    # CDATA 안에 &quot; &middot; 등 HTML 엔티티가 리터럴로 들어오므로 디코딩한다.
    return html.unescape(el.get_text(strip=True)) if el else ""


def _strip_html(raw_html: str) -> str:
    """OpenAPI 본문(DataContents)의 태그를 걷고 기사 하단 상용구를 제거한다.

    구분자를 공백이 아니라 줄바꿈으로 둔 것은 문단 경계를 남겨 두기 위해서다.
    한 줄로 이어붙이면 저작권 고지 같은 상용구를 라인 단위로 걸러낼 수 없고,
    그 문구가 요약 모델에 그대로 들어가 용어 풀이에 실려 나갔다.
    """
    if not raw_html:
        return ""
    text = BeautifulSoup(raw_html, "lxml").get_text(separator="\n", strip=True)
    return _clean_noise(html.unescape(text))


class PolicyBriefingApiCrawler:
    """정책브리핑 정책뉴스 OpenAPI 크롤러 — 부동산, 고용, 창업, 육아, 교육, 복지, 금융 7개 카테고리 수집."""

    SOURCE = "정책브리핑"

    # API는 한 번에 최대 3일(시작·종료일 차이 2일)까지만 조회 가능하므로
    # lookback_days를 3일 윈도우로 나눠 순회하며 백로그를 채운다.
    # (구 pressReleaseService와 달리 pageNo/numOfRows 페이지네이션이 없음 —
    #  날짜 윈도우당 한 번의 호출로 전체 결과가 반환된다.)
    def __init__(self, lookback_days: int = 14):
        self.lookback_days = lookback_days

    def fetch(self) -> list[RawPolicy]:
        key = (os.environ.get("DATA_GO_KR_KEY") or "").strip()
        if not key:
            raise RuntimeError("DATA_GO_KR_KEY 환경변수가 설정되지 않았습니다")

        now = datetime.now(KST)
        results: list[RawPolicy] = []
        seen_urls: set[str] = set()

        days_done = 0
        while days_done < self.lookback_days:
            end = now - timedelta(days=days_done)
            start = end - timedelta(days=2)  # 3일(포함) 윈도우
            resp = requests.get(
                POLICY_NEWS_API,
                params={
                    "serviceKey": key,
                    "startDate": start.strftime("%Y%m%d"),
                    "endDate": end.strftime("%Y%m%d"),
                },
                headers=HEADERS,
                timeout=30,
            )
            resp.raise_for_status()
            soup = BeautifulSoup(resp.text, "xml")
            result_code = soup.find("resultCode")
            if result_code and result_code.get_text(strip=True) != "0":
                result_msg = soup.find("resultMsg")
                print(
                    f"  [{start.strftime('%m/%d')}~{end.strftime('%m/%d')}] "
                    f"API 오류 {result_code.get_text(strip=True)}: {result_msg.get_text(strip=True) if result_msg else ''}",
                    file=sys.stderr,
                )
                days_done += 3
                continue
            nodes = soup.find_all("NewsItem")
            parsed = self._parse_nodes(nodes)
            print(f"  [{start.strftime('%m/%d')}~{end.strftime('%m/%d')}]: API {len(nodes)}건 → 키워드 매칭 {len(parsed)}건")
            for p in parsed:
                if p.url in seen_urls:
                    continue
                seen_urls.add(p.url)
                results.append(p)
            days_done += 3

        return results

    @classmethod
    def _parse(cls, xml_text: str) -> list[RawPolicy]:
        soup = BeautifulSoup(xml_text, "xml")
        return cls._parse_nodes(soup.find_all("NewsItem"))

    @classmethod
    def _parse_nodes(cls, nodes) -> list[RawPolicy]:
        out: list[RawPolicy] = []

        for node in nodes:
            title = _text(node, "Title")
            ministry = _text(node, "MinisterCode")
            category = _classify_category(title)
            if category is None:
                continue

            content = _strip_html(node.find("DataContents").get_text() if node.find("DataContents") else "")

            news_id = _text(node, "NewsItemId")
            original_url = _text(node, "OriginalUrl")
            file_name = _text(node, "FileName")
            file_url = _text(node, "FileUrl")

            url = original_url or (
                f"https://www.korea.kr/briefing/pressReleaseView.do?newsId={news_id}"
            )
            out.append(
                RawPolicy(
                    url=url,
                    title=title,
                    source=ministry or cls.SOURCE,
                    published_at=cls._parse_date(_text(node, "ApproveDate")),
                    file_url=file_url or None,
                    file_type=cls._file_type(file_name),
                    html_content=content,
                    category=category,
                )
            )

        return out

    @staticmethod
    def _file_type(file_name: str) -> str | None:
        name = (file_name or "").lower()
        for ext in ("hwpx", "hwp", "pdf"):
            if name.endswith(f".{ext}"):
                return ext
        return None

    @staticmethod
    def _parse_date(approve_date: str) -> str:
        # 예: "05/30/2026 01:00:00"
        try:
            dt = datetime.strptime(approve_date, "%m/%d/%Y %H:%M:%S")
            return dt.strftime("%Y-%m-%dT%H:%M:%S+09:00")
        except (ValueError, TypeError):
            return datetime.now(KST).strftime("%Y-%m-%dT%H:%M:%S+09:00")


# 크롤러 레지스트리 — 새 소스 추가 시 여기에만 등록.
CRAWLERS = [
    PolicyBriefingApiCrawler(),    # 14일 백로그, API 키 필요
]
