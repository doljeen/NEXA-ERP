"""
추가 가상 회사. 기존 3개 회사(generate.py COMPANIES + hrfiles/)와 양식·규칙·사고가 다르게 회사마다 모듈 하나로 만든다.

각 모듈의 build(df, used, out_dir, seed)는 out/<id>/ 에 파일을 쓰고 answer_key(dict)를 돌려준다.
난수는 회사·용도별 random.Random("<id>:<용도>:<seed>")만 쓴다 (기존 회사 난수열을 건드리지 않음).
"""
import importlib
import json

from . import base


def build_all(df, used, out_dir, seed, only=None):
    """only: 회사 id 목록 (개발용). 인물은 회사별 조각에서 뽑으므로 일부만 만들어도 결과가 같다"""
    summary = []
    for cid in base.PARTS:
        if only and cid not in only:
            continue
        m = importlib.import_module("newco." + cid)
        key = m.build(df, used, out_dir, seed)
        (out_dir / m.CID / "answer_key.json").write_text(json.dumps(key, ensure_ascii=False, indent=2), encoding="utf-8")
        summary.append(base.summary_of(key))
    return summary
