from fastapi import Header


def get_user_id(x_user_id: str | None = Header(default=None)) -> str | None:
    """게이트웨이가 넘겨주는 사번(X-User-Id). 아직 안 넘겨줬다면 None"""
    return x_user_id