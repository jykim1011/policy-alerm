import io
import os
import re
import subprocess
import tempfile
import zipfile
import xml.etree.ElementTree as ET

import pymupdf
from bs4 import BeautifulSoup

# 한글 문서의 이미지 대체텍스트 노이즈. 요약 입력을 오염시키므로 라인 단위로 제거한다.
# (국토부 hwpx에서 관찰: "그림입니다.", "원본 그림의 이름: ….png" 등)
_NOISE_LINE = re.compile(
    r"^(그림입니다\.?"
    r"|원본 그림의 (이름|크기)\s*:.*"
    r"|사진 찍은 날짜\s*:.*"
    r"|프로그램 이름\s*:.*)$"
)


def _clean_noise(text: str) -> str:
    return "\n".join(l for l in text.splitlines() if not _NOISE_LINE.match(l.strip()))


def extract_text(file_content: bytes, file_type: str) -> str:
    """파일 타입에 따라 텍스트를 추출한다. 실패 시 빈 문자열 반환."""
    try:
        if file_type == "hwpx":
            return _clean_noise(_extract_hwpx(file_content))
        elif file_type == "hwp":
            return _clean_noise(_extract_hwp(file_content))
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
        if result.returncode != 0:
            raise subprocess.SubprocessError(f"hwp5txt failed: {result.stderr}")
        return result.stdout
    finally:
        os.unlink(tmp_path)


def _extract_pdf(content: bytes) -> str:
    """PDF에서 텍스트 추출"""
    doc = pymupdf.open(stream=content, filetype="pdf")
    return "\n".join(page.get_text() for page in doc)


def _extract_html(html: str) -> str:
    """HTML 본문에서 텍스트 추출"""
    soup = BeautifulSoup(html, "lxml")
    for tag in soup(["script", "style", "nav", "footer", "header"]):
        tag.decompose()
    return soup.get_text(separator="\n", strip=True)
