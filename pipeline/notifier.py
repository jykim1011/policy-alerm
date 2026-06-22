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


def notify_new_policy(item: PolicyItem, batch: str, db=None) -> None:
    """Firestore new_policies/{id} 에 문서를 생성해 Cloud Function을 트리거한다."""
    client = _get_db(db)
    client.collection("new_policies").document(item.id).set({
        # id를 본문에도 저장한다. Cloud Function v2(gen2)의 event.params 는 한글 등
        # 비ASCII 문서 ID를 모지바케로 깨뜨리므로(firebase-functions#1459), 함수는
        # 경로 파라미터가 아닌 이 본문 id를 사용해야 알림 policy_id 가 CDN 파일명과 맞다.
        "id": item.id,
        "category": item.category,
        "subcategory": item.subcategory,
        "title": item.title,
        "batch": batch,  # "morning" | "evening"
    })
