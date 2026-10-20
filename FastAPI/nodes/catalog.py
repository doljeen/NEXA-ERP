
CATEGORIES = [
    {"code": "input", "name": "입력"},
    {"code": "ai", "name": "AI"},
    {"code": "rule", "name": "규칙·분기"},
    {"code": "human", "name": "사람 확인"},
    {"code": "output", "name": "문서·알림"},
]

NODES = [
    {
        "node_type": "file_upload",
        "name": "파일 업로드",
        "category": "input",
        "description": "영수증·서류 파일을 받음",
        "config_fields": [
            {"key": "accept", "label": "허용 형식", "type": "select",
             "options": ["이미지", "PDF", "엑셀"], "required": True},
        ],
        "branches": [],
        "outputs": [
            {"key": "file_ids", "label": "파일 목록", "type": "list"},
        ],
    },
    {
        "node_type": "ocr_extract",
        "name": "서류 항목 추출",
        "category": "ai",
        "description": "OCR + LLM으로 가맹점·금액·날짜 추출",
        "config_fields": [
            {"key": "fields", "label": "추출할 항목", "type": "text", "required": True},
        ],
        "branches": ["성공", "확인 필요"],
        "outputs": [
            {"key": "items", "label": "추출 결과", "type": "object"},
            {"key": "confidence", "label": "신뢰도", "type": "number"},
        ],
    },
    {
        "node_type": "rag_search",
        "name": "규정 근거 검색",
        "category": "ai",
        "description": "사내 규정에서 판단 근거를 찾음",
        "config_fields": [
            {"key": "doc_set", "label": "규정 문서", "type": "select",
             "options": ["경비 규정", "휴가 규정"], "required": True},
        ],
        "branches": ["적합", "부적합", "애매함"],
        "outputs": [
            {"key": "evidence", "label": "근거 조항", "type": "text"},
        ],
    },
    {
        "node_type": "approval",
        "name": "결재 요청",
        "category": "human",
        "description": "결재를 올리고 완료될 때까지 대기",
        "config_fields": [
            {"key": "form_code", "label": "결재 양식", "type": "select",
             "options": ["경비 품의서", "휴가 신청서"], "required": True},
        ],
        "branches": ["승인", "반려"],
        "outputs": [
            {"key": "approval_id", "label": "결재 번호", "type": "text"},
        ],
    },
]