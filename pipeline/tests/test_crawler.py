import pytest
from pipeline.crawler import (
    PolicyBriefingApiCrawler,
    RssPolicyBriefingCrawler,
    load_seen,
    save_seen,
    is_new_policy,
)

# 실제 data.go.kr 1371000 pressReleaseService 응답 구조를 본뜬 샘플.
SAMPLE_XML = """<?xml version="1.0" encoding="UTF-8"?>
<response>
  <header><resultCode>0</resultCode><resultMsg>NORMAL_SERVICE</resultMsg></header>
  <body>
    <NewsItem>
      <NewsItemId>156764520</NewsItemId>
      <ApproveDate>05/30/2026 01:00:00</ApproveDate>
      <MinisterCode>국토교통부</MinisterCode>
      <Title><![CDATA[수도권 전세시장 안정 대책 발표]]></Title>
      <DataContents><![CDATA[<p style="x">전세 임대 공급을 확대한다.</p>]]></DataContents>
      <OriginalUrl>https://www.korea.kr/briefing/pressReleaseView.do?newsId=156764520</OriginalUrl>
      <FileUrl>https://www.korea.kr/common/download.do?tblKey=GMN&amp;fileId=1</FileUrl>
      <FileName>전세대책.hwpx</FileName>
    </NewsItem>
    <NewsItem>
      <NewsItemId>156764519</NewsItemId>
      <ApproveDate>05/30/2026 00:00:00</ApproveDate>
      <MinisterCode>과학기술정보통신부</MinisterCode>
      <Title><![CDATA[주요 7개국 디지털 기술 장관회의 참석]]></Title>
      <DataContents><![CDATA[<p>인공지능 안전을 논의한다.</p>]]></DataContents>
      <OriginalUrl>https://www.korea.kr/briefing/pressReleaseView.do?newsId=156764519</OriginalUrl>
      <FileUrl></FileUrl>
      <FileName></FileName>
    </NewsItem>
  </body>
</response>"""


def test_parse_keeps_only_real_estate_items():
    items = PolicyBriefingApiCrawler._parse(SAMPLE_XML)
    assert len(items) == 1
    assert "전세" in items[0].title


def test_parse_extracts_expected_fields():
    p = PolicyBriefingApiCrawler._parse(SAMPLE_XML)[0]
    assert p.url == "https://www.korea.kr/briefing/pressReleaseView.do?newsId=156764520"
    assert p.source == "국토교통부"
    assert p.published_at == "2026-05-30T01:00:00+09:00"
    assert p.file_type == "hwpx"
    assert "전세 임대 공급을 확대한다" in p.html_content
    assert "<p" not in p.html_content  # HTML 태그는 제거됨


def test_parse_decodes_html_entities_in_title():
    xml = """<response><body><NewsItem>
      <NewsItemId>1</NewsItemId>
      <ApproveDate>05/30/2026 00:00:00</ApproveDate>
      <MinisterCode>국토교통부</MinisterCode>
      <Title><![CDATA[&quot;전세사기&quot; 예방&middot;대응 강화]]></Title>
      <DataContents><![CDATA[<p>내용</p>]]></DataContents>
      <OriginalUrl>https://example.com/1</OriginalUrl>
    </NewsItem></body></response>"""
    p = PolicyBriefingApiCrawler._parse(xml)[0]
    assert p.title == '"전세사기" 예방·대응 강화'


def test_parse_empty_body_returns_empty_list():
    xml = '<response><header><resultCode>0</resultCode></header><body></body></response>'
    assert PolicyBriefingApiCrawler._parse(xml) == []


def test_fetch_raises_without_api_key(monkeypatch):
    monkeypatch.delenv("DATA_GO_KR_KEY", raising=False)
    with pytest.raises(RuntimeError):
        PolicyBriefingApiCrawler().fetch()


def test_load_seen_returns_set(tmp_path):
    seen_file = tmp_path / "seen.json"
    seen_file.write_text('{"seen": ["abc123", "def456"]}')
    assert load_seen(str(seen_file)) == {"abc123", "def456"}


def test_save_and_load_roundtrip(tmp_path):
    seen_file = tmp_path / "seen.json"
    seen_file.write_text('{"seen": []}')
    save_seen({"hash1", "hash2"}, str(seen_file))
    assert load_seen(str(seen_file)) == {"hash1", "hash2"}


def test_is_new_policy():
    seen = {"existing_hash"}
    assert is_new_policy("existing_hash", seen) is False
    assert is_new_policy("new_hash", seen) is True


# ── RssPolicyBriefingCrawler ──────────────────────────────────────────────────

SAMPLE_RSS = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>정책브리핑 보도자료</title>
    <item>
      <title><![CDATA[기준금리 0.25%p 인하 결정]]></title>
      <link>https://www.korea.kr/briefing/pressReleaseView.do?newsId=200001</link>
      <description><![CDATA[<p>한국은행이 기준금리를 0.25%p 인하했다.</p>]]></description>
      <pubDate>Mon, 09 Jun 2026 09:00:00 +0900</pubDate>
      <author>한국은행</author>
    </item>
    <item>
      <title><![CDATA[인공지능 안전 국제협력 강화]]></title>
      <link>https://www.korea.kr/briefing/pressReleaseView.do?newsId=200002</link>
      <description><![CDATA[<p>AI 안전을 논의한다.</p>]]></description>
      <pubDate>Mon, 09 Jun 2026 10:00:00 +0900</pubDate>
      <author>과학기술정보통신부</author>
    </item>
  </channel>
</rss>"""


def test_rss_parse_filters_unmatched_category():
    items = RssPolicyBriefingCrawler._parse(SAMPLE_RSS)
    assert len(items) == 1
    assert "기준금리" in items[0].title


def test_rss_parse_extracts_fields():
    item = RssPolicyBriefingCrawler._parse(SAMPLE_RSS)[0]
    assert item.url == "https://www.korea.kr/briefing/pressReleaseView.do?newsId=200001"
    assert item.source == "한국은행"
    assert item.published_at == "2026-06-09T09:00:00+09:00"
    assert item.category == "금융"
    assert item.file_url is None
    assert item.file_type is None
    assert "한국은행이 기준금리를" in item.html_content


def test_rss_parse_empty_feed():
    xml = '<?xml version="1.0"?><rss version="2.0"><channel></channel></rss>'
    assert RssPolicyBriefingCrawler._parse(xml) == []


def test_rss_parse_date_fallback():
    assert RssPolicyBriefingCrawler._parse_date("invalid") != ""  # 폴백 날짜 반환
