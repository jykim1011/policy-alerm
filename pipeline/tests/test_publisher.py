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
    index = json.loads((tmp_path / "policies" / "index.json").read_text(encoding="utf-8"))
    assert len(index["items"]) == 1
    assert index["items"][0]["id"] == "molit-2026-05-29-001"

from pipeline.publisher import _update_archive_index

def test_update_archive_creates_year_file(tmp_path):
    item = _make_item()
    _update_archive_index(item, docs_root=str(tmp_path))

    year_path = tmp_path / "archive" / "2026.json"
    assert year_path.exists()
    data = json.loads(year_path.read_text(encoding="utf-8"))
    assert data["year"] == 2026
    assert data["total"] == 1
    assert data["items"][0]["id"] == "molit-2026-05-29-001"
    assert data["items"][0]["summary_preview"] == "가점 확대..."

def test_update_archive_creates_years_index(tmp_path):
    item = _make_item()
    _update_archive_index(item, docs_root=str(tmp_path))

    idx_path = tmp_path / "archive" / "index.json"
    assert idx_path.exists()
    idx = json.loads(idx_path.read_text(encoding="utf-8"))
    assert 2026 in idx["years"]

def test_update_archive_deduplicates_same_id(tmp_path):
    item = _make_item()
    _update_archive_index(item, docs_root=str(tmp_path))
    _update_archive_index(item, docs_root=str(tmp_path))

    data = json.loads((tmp_path / "archive" / "2026.json").read_text(encoding="utf-8"))
    assert data["total"] == 1

def test_update_index_keeps_max_50(tmp_path):
    for i in range(55):
        item = _make_item()
        item.id = f"policy-{i:03d}"
        publish_policy(item, docs_root=str(tmp_path))
        update_index(item, docs_root=str(tmp_path))
    index = json.loads((tmp_path / "policies" / "index.json").read_text(encoding="utf-8"))
    assert len(index["items"]) == 50
