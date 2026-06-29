import json
from unittest.mock import MagicMock, patch
from pipeline.notifier import notify_new_batch, _load_service_account
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

def test_load_service_account_strips_utf8_bom():
    """FIREBASE_SERVICE_ACCOUNT 시크릿에 UTF-8 BOM이 붙어도 파싱돼야 한다."""
    payload = {"type": "service_account", "project_id": "policy-alerm"}
    raw = "﻿" + json.dumps(payload)
    assert _load_service_account(raw) == payload


def test_load_service_account_handles_surrounding_whitespace():
    payload = {"type": "service_account"}
    assert _load_service_account("  \n" + json.dumps(payload) + "\n  ") == payload


def test_notify_new_batch_writes_single_document():
    mock_db = MagicMock()
    mock_doc_ref = MagicMock()
    mock_db.collection.return_value.document.return_value = mock_doc_ref

    items = [_make_item()]
    notify_new_batch(items, batch="morning", run_id="20260629T093000Z_morning", db=mock_db)

    mock_db.collection.assert_called_with("new_policy_batches")
    mock_db.collection.return_value.document.assert_called_with("20260629T093000Z_morning")
    mock_doc_ref.set.assert_called_once()
    data = mock_doc_ref.set.call_args[0][0]
    assert data["run_id"] == "20260629T093000Z_morning"
    assert data["batch"] == "morning"
    assert len(data["policies"]) == 1
    p = data["policies"][0]
    # policy_id는 본문 id에서 와야 한다(gen2 event.params 모지바케 회피, #1459).
    assert p["id"] == "molit-2026-05-29-001"
    assert p["category"] == "부동산"
    assert p["subcategory"] == "청약"
    assert p["title"] == "청약 제도 개편"


def test_notify_new_batch_empty_items_writes_nothing():
    mock_db = MagicMock()
    notify_new_batch([], batch="morning", run_id="x", db=mock_db)
    mock_db.collection.assert_not_called()
