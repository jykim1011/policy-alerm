import json
import os

import firebase_admin
from firebase_admin import credentials, firestore

from pipeline.models import PolicyItem


def _load_service_account(raw: str) -> dict:
    """FIREBASE_SERVICE_ACCOUNT 시크릿을 파싱한다.

    값에 UTF-8 BOM이나 앞뒤 공백이 섞이면 json.loads가
    'Unexpected UTF-8 BOM' 에러를 내므로 먼저 제거한다.
    """
    return json.loads(raw.lstrip("﻿").strip())


def _get_db(db=None):
    if db is not None:
        return db
    if not firebase_admin._apps:
        service_account = _load_service_account(os.environ["FIREBASE_SERVICE_ACCOUNT"])
        cred = credentials.Certificate(service_account)
        firebase_admin.initialize_app(cred)
    return firestore.client()


def notify_new_batch(items, batch: str, run_id: str, db=None) -> None:
    """확인된 새 정책 전체를 new_policy_batches/{run_id} 문서 1건으로 써서 Cloud Function을
    회차당 한 번만 트리거한다(푸시 코얼레싱). items가 비면 아무것도 쓰지 않는다.

    문서 본문에 정책별 id를 저장한다. Cloud Function v2(gen2)의 event.params 는 한글 등
    비ASCII 문서 ID를 모지바케로 깨뜨리므로(firebase-functions#1459), 함수는 이 본문 id를
    써야 알림 policy_id 가 CDN 파일명과 일치한다.
    """
    if not items:
        return
    client = _get_db(db)
    client.collection("new_policy_batches").document(run_id).set({
        "run_id": run_id,
        "batch": batch,  # "morning" | "evening" — 분류 라벨
        "created_at": firestore.SERVER_TIMESTAMP,
        "policies": [
            {
                "id": item.id,
                "category": item.category,
                "subcategory": item.subcategory,
                "title": item.title,
            }
            for item in items
        ],
    })
