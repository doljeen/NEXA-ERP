"""
가상 회사 데이터를 이관 서비스에 올리고 정답지(answer_key.json)와 비교한다.

- 매핑 정확도: 추천된 컬럼 매핑 == 정답 매핑
- 오류 검출률: 정답지의 오류 (rowNo, field, code) 중 이관 서비스가 찾은 비율 (유형별)
- 오탐: 정답지에 없는데 찾은 행 단위 오류

사용: python evaluate.py <out 폴더> [--base http://localhost:8087]
"""
import argparse
import json
import urllib.error
import urllib.request
import uuid
from collections import Counter, defaultdict
from pathlib import Path

API = "/api/v1/admin/migrations"


def call(base, path, data=None, ctype="application/json", method=None):
    req = urllib.request.Request(base + path, data=data, method=method,
                                 headers={"Content-Type": ctype} if data is not None else {})
    try:
        body = json.load(urllib.request.urlopen(req, timeout=120))
    except urllib.error.HTTPError as e:
        body = json.load(e)
    if not body.get("success"):
        raise RuntimeError(f"{path}: {body.get('error')}")
    return body["data"]


def upload(base, mid, entity, path):
    boundary = "----" + uuid.uuid4().hex
    head = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\n'
            f"Content-Type: application/octet-stream\r\n\r\n").encode("utf-8")
    body = head + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    return call(base, f"{API}/{mid}/files?entity={entity}", body, f"multipart/form-data; boundary={boundary}")


def all_issues(base, mid, file_id):
    issues, page = [], 0
    while True:
        d = call(base, f"{API}/{mid}/issues?fileId={file_id}&page={page}&size=500")
        issues += d["content"]
        if (page + 1) * 500 >= d["totalElements"]:
            return issues
        page += 1


def evaluate_company(base, cdir):
    key = json.loads((cdir / "answer_key.json").read_text(encoding="utf-8"))
    mid = call(base, API, json.dumps({"companyId": 1, "name": key["company"] + " 평가"}).encode("utf-8"))["migrationId"]

    result = {"company": key["company"], "mapping": {}, "detection": {}, "falsePositives": Counter(), "fileLevel": []}
    found_by_entity = {}
    for entity, fname in key["files"].items():
        up = upload(base, mid, entity, cdir / fname)
        detail = call(base, f"{API}/{mid}/files/{up['fileId']}")
        expected = key["expectedMapping"][entity]
        got = {m["sourceColumn"]: m["targetField"] for m in detail["columnMappings"]}
        wrong = {c: {"expected": expected[c], "got": got.get(c)} for c in expected if expected[c] != got.get(c)}
        result["mapping"][entity] = {"correct": len(expected) - len(wrong), "total": len(expected), "wrong": wrong}
        issues = all_issues(base, mid, up["fileId"])
        result["fileLevel"] += [i["message"] for i in issues if i["rowNo"] is None]
        found_by_entity[entity] = {(i["rowNo"], i["field"], i["code"]) for i in issues if i["rowNo"] is not None}

    found = found_by_entity["HR_EMPLOYEE"]
    expected_errors = {(e["rowNo"], e["field"], e["code"]): e for e in key["errors"]}
    by_code = defaultdict(lambda: {"expected": 0, "found": 0, "detectable": True})
    for k, e in expected_errors.items():
        s = by_code[e["code"]]
        s["expected"] += 1
        s["detectable"] = e["detectable"]
        s["found"] += k in found
    result["detection"] = dict(by_code)
    for row_no, field, code in found - expected_errors.keys():
        result["falsePositives"][code] += 1
    result["falsePositives"] = dict(result["falsePositives"])
    result["orgIssues"] = len(found_by_entity["HR_ORG"])
    return result


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--base", default="http://localhost:8087")
    args = ap.parse_args()
    out = Path(args.out)
    results = [evaluate_company(args.base, d) for d in sorted(out.iterdir()) if (d / "answer_key.json").exists()]
    (out / "evaluation.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = ["# 이관 검증 평가 결과", ""]
    total_det = Counter()
    for r in results:
        lines.append(f"## {r['company']}")
        for entity, m in r["mapping"].items():
            lines.append(f"- 매핑 {entity}: {m['correct']}/{m['total']}" + (f" (틀림: {m['wrong']})" if m["wrong"] else ""))
        lines.append("")
        lines.append("| 오류 유형 | 정답 | 검출 | 검출률 | 비고 |")
        lines.append("|---|---|---|---|---|")
        for code, s in sorted(r["detection"].items()):
            rate = s["found"] / s["expected"] if s["expected"] else 0
            lines.append(f"| {code} | {s['expected']} | {s['found']} | {rate:.0%} | {'' if s['detectable'] else '아직 못 잡는 유형'} |")
            if s["detectable"]:
                total_det["expected"] += s["expected"]
                total_det["found"] += s["found"]
        lines.append("")
        lines.append(f"- 오탐(정답지에 없는데 검출): {r['falsePositives'] or '없음'}")
        lines.append(f"- 파일 단위 오류: {r['fileLevel'] or '없음'}")
        lines.append(f"- 조직 파일 행 오류: {r['orgIssues']}")
        lines.append("")
    rate = total_det["found"] / total_det["expected"] if total_det["expected"] else 0
    lines.insert(2, f"**전체 검출률 (잡을 수 있는 유형 기준): {total_det['found']}/{total_det['expected']} = {rate:.1%}**\n")
    (out / "evaluation.md").write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
