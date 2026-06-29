import json
from unittest.mock import patch
import pipeline.main as main


def _write_pending(tmp_path, entries):
    f = tmp_path / "pending_notify.json"
    f.write_text(json.dumps(entries, ensure_ascii=False), encoding="utf-8")
    return f


def test_notify_pending_sends_one_batch_for_confirmed(tmp_path, monkeypatch):
    pending = [
        {"id": "a", "category": "부동산", "subcategory": "청약", "title": "t1", "batch": "morning"},
        {"id": "b", "category": "고용", "subcategory": "고용", "title": "t2", "batch": "morning"},
    ]
    f = _write_pending(tmp_path, pending)
    monkeypatch.setattr(main, "PENDING_FILE", f)
    monkeypatch.setenv("BATCH", "morning")

    with patch.object(main, "_wait_for_cdn", return_value=True), \
         patch.object(main, "notify_new_batch") as mock_batch:
        main.notify_pending()

    # 배치 트리거는 정확히 한 번, 확인된 2건 전부 포함.
    mock_batch.assert_called_once()
    items = mock_batch.call_args[0][0]
    assert [i.id for i in items] == ["a", "b"]
    # 전부 성공 시 pending 파일 삭제.
    assert not f.exists()


def test_notify_pending_requeues_cdn_failures(tmp_path, monkeypatch):
    pending = [
        {"id": "a", "category": "부동산", "subcategory": "청약", "title": "t1", "batch": "morning"},
        {"id": "b", "category": "고용", "subcategory": "고용", "title": "t2", "batch": "morning"},
    ]
    f = _write_pending(tmp_path, pending)
    monkeypatch.setattr(main, "PENDING_FILE", f)
    monkeypatch.setenv("BATCH", "morning")

    # a만 CDN 반영, b는 타임아웃.
    def fake_cdn(pid, **kw):
        return pid == "a"

    with patch.object(main, "_wait_for_cdn", side_effect=fake_cdn), \
         patch.object(main, "notify_new_batch") as mock_batch:
        try:
            main.notify_pending()
        except SystemExit:
            pass  # 실패분이 있으면 SystemExit (CI 실패 표시)

    items = mock_batch.call_args[0][0]
    assert [i.id for i in items] == ["a"]  # 확인된 a만 발송
    remaining = json.loads(f.read_text(encoding="utf-8"))
    assert [p["id"] for p in remaining] == ["b"]  # b는 재시도용으로 남음
