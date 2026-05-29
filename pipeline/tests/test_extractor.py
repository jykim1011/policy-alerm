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
