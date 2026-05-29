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
