from unittest.mock import MagicMock, patch

from pipeline import main
from pipeline.crawler import find_page_attachment

PAGE = """<html><body><div class="filedown"><dl><dd>
<p><span><a href="/common/download.do?fileId=1">설명자료.pdf</a></span>
<span><a class="view" href="/common/docViewer.do?fileId=1">바로보기</a></span></p>
<p><span><a href="/common/download.do?fileId=2">설명자료.hwpx</a></span></p>
</dd></dl></div></body></html>"""


def _page(html):
    resp = MagicMock()
    resp.content = html.encode("utf-8")
    return resp


def test_find_page_attachment_prefers_hwpx():
    with patch("pipeline.crawler.requests.get", return_value=_page(PAGE)):
        url, ext = find_page_attachment("https://www.korea.kr/briefing/actuallyView.do?newsId=1")
    assert (url, ext) == ("https://www.korea.kr/common/download.do?fileId=2", "hwpx")


def test_find_page_attachment_none_when_no_files():
    with patch("pipeline.crawler.requests.get", return_value=_page("<html></html>")):
        assert find_page_attachment("https://www.korea.kr/x") is None


def test_stub_body_without_api_file_uses_page_attachment():
    """OpenAPI가 FileUrl을 비워 보내도 상세 페이지의 첨부로 원문을 채운다(제목만 요약 방지)."""
    with patch.object(main, "find_page_attachment", return_value=("u", "hwpx")), \
         patch.object(main, "download_file", return_value=b"x"), \
         patch.object(main, "extract_text", return_value="첨부 원문 " * 100):
        text = main._with_attachment_text("", None, None, "https://www.korea.kr/x")
    assert text.startswith("첨부 원문")


def test_title_key_matches_joint_press_releases():
    assert main._title_key("부동산정책, 국민의 목소리를 듣습니다.") == \
        main._title_key("부동산정책, 국민의 목소리를 듣습니다")


def test_falls_back_to_page_attachment_when_api_file_yields_nothing():
    """CI에서 API 첨부(hwp) 추출이 0자를 돌려주면 페이지의 hwpx·pdf로 다시 시도한다."""
    def fake_extract(_bytes, ftype):
        return "" if ftype == "hwp" else "페이지 첨부 원문 " * 100

    with patch.object(main, "find_page_attachment", return_value=("page-url", "pdf")), \
         patch.object(main, "download_file", return_value=b"x"), \
         patch.object(main, "extract_text", side_effect=fake_extract):
        text = main._with_attachment_text("짧은 본문", "api-url", "hwp", "https://www.korea.kr/x")
    assert text.startswith("페이지 첨부 원문")


def test_keeps_body_when_all_attachments_fail():
    with patch.object(main, "find_page_attachment", side_effect=ConnectionError("reset")), \
         patch.object(main, "download_file", side_effect=ConnectionError("reset")):
        assert main._with_attachment_text("본문", "api-url", "hwp", "https://x") == "본문"


def test_run_fails_when_sources_collect_nothing(tmp_path, monkeypatch):
    """14일 조회에서 0건이면 수집원 고장이다. CI가 초록불로 넘어가지 않게 실패로 끝낸다."""
    import pytest

    class Empty:
        SOURCE = "empty"

        def fetch(self):
            return []

    monkeypatch.chdir(tmp_path)
    monkeypatch.setattr(main, "CRAWLERS", [Empty()])
    monkeypatch.setattr(main, "load_seen", lambda: set())
    monkeypatch.setattr(main, "save_seen", lambda seen: None)
    monkeypatch.setattr(main, "PENDING_FILE", tmp_path / "pending.json")
    with pytest.raises(SystemExit, match="수집 0건"):
        main.run("morning")
