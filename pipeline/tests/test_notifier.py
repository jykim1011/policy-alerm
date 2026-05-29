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
