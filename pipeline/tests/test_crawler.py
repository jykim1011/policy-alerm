from unittest.mock import patch, MagicMock
from pipeline.crawler import MolitCrawler, load_seen, save_seen, is_new_policy


def test_load_seen_returns_set(tmp_path):
    seen_file = tmp_path / "seen.json"
    seen_file.write_text('{"seen": ["abc123", "def456"]}')
    result = load_seen(str(seen_file))
    assert result == {"abc123", "def456"}


def test_save_and_load_roundtrip(tmp_path):
    seen_file = tmp_path / "seen.json"
    seen_file.write_text('{"seen": []}')
    save_seen({"hash1", "hash2"}, str(seen_file))
    result = load_seen(str(seen_file))
    assert result == {"hash1", "hash2"}


def test_is_new_policy():
    seen = {"existing_hash"}
    assert is_new_policy("existing_hash", seen) is False
    assert is_new_policy("new_hash", seen) is True
