"""기존 docs/policies/*.json 에서 docs/archive/ 를 소급 생성하는 일회성 스크립트."""
import json
from pathlib import Path

from pipeline.publisher import _update_archive_index
from pipeline.models import PolicyItem, PolicySummary

DOCS_ROOT = "docs"


def main() -> None:
    policies_dir = Path(DOCS_ROOT) / "policies"
    count = 0
    for f in sorted(policies_dir.glob("*.json")):
        if f.name == "index.json":
            continue
        d = json.loads(f.read_text(encoding="utf-8"))
        summary = None
        if d.get("summary"):
            s = d["summary"]
            summary = PolicySummary(
                what_changed=s["what_changed"],
                who_is_affected=s["who_is_affected"],
                when_effective=s["when_effective"],
                key_points=s["key_points"],
            )
        item = PolicyItem(
            id=d["id"],
            category=d["category"],
            subcategory=d["subcategory"],
            title=d["title"],
            source=d["source"],
            source_url=d.get("source_url", ""),
            file_url=d.get("file_url"),
            file_type=d.get("file_type"),
            published_at=d["published_at"],
            summary=summary,
        )
        _update_archive_index(item, docs_root=DOCS_ROOT)
        count += 1
    print(f"소급 생성 완료: {count}건")


if __name__ == "__main__":
    main()
