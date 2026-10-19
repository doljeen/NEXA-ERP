def get_categories() -> list[dict]:
    # 임시 데이터, 나중에 DB 조회로 교체 예정
    return[
        {"code": "trigger", "name": "시작"},
        {"code": "ai", "name": "AI"},
        {"code": "approval", "name": "결재"},
    ]