"""
시연용 이관 파일: 조직.xlsx(HR_ORG 32행)와 사원.xlsx(HR_EMPLOYEE 1,252행).

프론트 시연 대본(DOCS/frontend-design.md 6.7·8장, frontend-requirements.md 8장 #11)에 맞춘다.
- 조직: 회사 1 + 본부 5 + 팀 26. 부서유형·부서장 사번이 모두 맞게 채워져 있어 이슈 없이 바로 적재된다
- 사원: 날짜 표기 섞임(자동 정리), 고용형태 줄임말(AI 값 추천), 휴대폰 앞자리 0 빠짐(확인 필요), 입사일 빈 칸 1건(적재 불가)
- 사원_적재불가0.xlsx: 입사일 빈 칸만 채운 판. 행 수정(P1)을 빼면 이 파일로 시연한다
가상 회사 데이터와 같은 합성 인물(Nemotron-Personas-Korea)을 쓴다. 이름 말고는 인물 정보를 쓰지 않는다.

사용: python -I demo.py <personas.parquet> <출력 폴더> [--seed 7]
"""
import argparse
import datetime as dt
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from openpyxl import Workbook

from generate import POSITIONS, dept_of, load_personas

COMPANY = "넥사데모(주)"
DOMAIN = "nexa-demo.example"
CUTOVER = dt.date(2026, 12, 1)
SIZE = 1252

# 본부 → (팀, 직군, 직종, 인원). 인원 합 + 대표 1 + 본부장 5 = SIZE
HQS = {
    "경영지원본부": [("인사팀", "경영지원", "인사", 28), ("총무팀", "경영지원", "총무", 34), ("재무팀", "경영지원", "재무회계", 31),
                ("법무팀", "경영지원", "법무", 12), ("구매팀", "경영지원", "구매", 25)],
    "영업본부": [("영업1팀", "영업", "국내영업", 62), ("영업2팀", "영업", "국내영업", 58), ("영업3팀", "영업", "국내영업", 55),
             ("해외영업팀", "영업", "해외영업", 40), ("마케팅팀", "영업", "마케팅", 38), ("고객지원팀", "영업", "고객지원", 91)],
    "개발본부": [("플랫폼개발팀", "개발", "SW", 54), ("서비스개발팀", "개발", "SW", 80), ("데이터팀", "개발", "SW", 36),
             ("인프라팀", "개발", "SW", 30), ("보안팀", "개발", "SW", 18), ("서비스기획팀", "개발", "기획·디자인", 32)],
    "생산본부": [("생산1팀", "생산", "제조", 120), ("생산2팀", "생산", "제조", 110), ("생산3팀", "생산", "제조", 80),
             ("품질관리팀", "생산", "품질", 46), ("설비팀", "생산", "제조", 34), ("물류팀", "생산", "물류", 52)],
    "연구소": [("연구1팀", "연구", "연구개발", 30), ("연구2팀", "연구", "연구개발", 25), ("연구3팀", "연구", "연구개발", 25)],
}
# 연구소 인원은 개발 직업군에서 뽑는다
POOL_OF = {"연구소": "개발본부"}

EMP_HEADERS = ["사번", "성명", "이메일", "부서", "직위", "직급", "직책", "직군", "직종", "직무", "고용형태",
               "입사일", "생년월일", "성별", "휴대폰", "재직상태", "퇴사일"]
ORG_HEADERS = ["부서코드", "부서명", "상위부서", "부서유형", "부서장 사번", "정렬순서"]


def build(personas, rng):
    """정상 데이터: 조직 32행, 사원 SIZE행 (나이가 많은 순으로 대표·본부장·팀장)"""
    org = [{"code": "D000", "name": COMPANY, "parent": None, "type": "회사"}]
    seats = []  # (본부, 팀, 직군, 직종, 인원)
    for h, (hq, teams) in enumerate(HQS.items()):
        hq_code = "D%d00" % (h + 1)
        org.append({"code": hq_code, "name": hq, "parent": "D000", "type": "본부"})
        for t, (team, family, kind, n) in enumerate(teams):
            org.append({"code": "D%d%02d" % (h + 1, (t + 1) * 10), "name": team, "parent": hq_code, "type": "팀"})
            seats.append((hq, team, family, kind, n))
    assert len(org) == 32 and sum(s[4] for s in seats) + 1 + len(HQS) == SIZE

    pools = {hq: [] for hq in HQS}
    for p in personas:
        hq = dept_of(p["occupation"])[0]
        for target in HQS:
            if POOL_OF.get(target, target) == hq:
                pools[target].append(p)
    rest = iter(p for p in personas)
    used = set()

    def take(hq, oldest=False):
        if oldest:  # 대표·본부장은 그 직업군에서 나이가 가장 많은 사람
            pools[hq].sort(key=lambda p: (p["uuid"] not in used, p["age"]))
        while pools[hq]:
            p = pools[hq].pop()
            if p["uuid"] not in used:
                used.add(p["uuid"])
                return p
        for p in rest:
            if p["uuid"] not in used:
                used.add(p["uuid"])
                return p
        raise RuntimeError("인물이 모자랍니다")

    people = [("D000", COMPANY, "경영지원", "경영", take("경영지원본부", oldest=True))]
    for hq in HQS:
        people.append((next(o["code"] for o in org if o["name"] == hq), hq, *HQS[hq][0][1:3], take(hq, oldest=True)))
    for hq in HQS:  # 나머지는 섞어서
        rng.shuffle(pools[hq])
    for hq, team, family, kind, n in seats:
        code = next(o["code"] for o in org if o["name"] == team)
        people += [(code, team, family, kind, take(hq)) for _ in range(n)]

    employees, head_of = [], {}
    for n, (code, dept, family, kind, p) in enumerate(people):
        age = int(p["age"])
        birth = dt.date(CUTOVER.year - 1 - age, rng.randint(1, 12), rng.randint(1, 28))
        hire = dt.date(birth.year + rng.randint(23, min(age, 35)), rng.randint(1, 12), rng.randint(1, 28))
        if hire >= CUTOVER:
            hire = CUTOVER - dt.timedelta(days=rng.randint(30, 400))
        position, grade = next((pos, g) for limit, pos, g in POSITIONS if age <= limit)
        employees.append({
            "empNo": "N%05d" % (n + 1), "name": p["name"], "email": "n%05d@%s" % (n + 1, DOMAIN),
            "dept": dept, "code": code, "position": position, "grade": grade, "duty": "팀원",
            "jobFamily": family, "jobKind": kind, "jobRole": p["occupation"][:30],
            "empType": rng.choices(["정규직", "계약직", "파트타임"], [90, 8, 2])[0],
            "hireDate": hire, "birthDate": birth, "gender": "남" if p["sex"] == "남자" else "여",
            "mobile": "010-%04d-%04d" % (rng.randint(1000, 9999), rng.randint(0, 9999)),
            "status": "재직", "resignDate": None,
        })
    # 부서마다 가장 나이 많은 사람을 부서장으로 (대표이사·본부장·팀장)
    duty_of = {"회사": "대표이사", "본부": "본부장", "팀": "팀장"}
    type_of = {o["code"]: o["type"] for o in org}
    for e in sorted(employees, key=lambda e: e["birthDate"]):
        if e["code"] not in head_of:
            head_of[e["code"]] = e["empNo"]
            e["duty"] = duty_of[type_of[e["code"]]]
            e["empType"] = "정규직"
    for o in org:
        o["head"] = head_of[o["code"]]
    # 퇴직자 약 4% (부서장 제외)
    for e in employees:
        if e["duty"] == "팀원" and rng.random() < 0.04:
            e["status"] = "퇴직"
            e["resignDate"] = min(CUTOVER - dt.timedelta(days=1), e["hireDate"] + dt.timedelta(days=rng.randint(200, 3000)))
    return org, employees


def dirty(employees, rng):
    """시연용 문제를 넣는다. 값 오류는 재직 중인 팀원에게만 넣는다. 넣은 건수를 돌려준다"""
    members = [e for e in employees if e["duty"] == "팀원" and e["status"] == "재직"]
    rng.shuffle(members)
    counts = {"DATE_FORMAT": 0}
    for e in employees:  # 날짜 표기 섞임 → 자동 정리
        for field in ("hireDate", "birthDate"):
            if rng.random() < 0.08:
                d = e[field]
                e[field] = rng.choice(["%d.%d.%d" % (d.year, d.month, d.day), d.strftime("%Y%m%d"), d.strftime("%Y/%m/%d")])
                counts["DATE_FORMAT"] += 1
    for e, v in zip(members[:8], ["정규"] * 5 + ["계약"] * 3):  # 줄임말 → AI 값 추천
        e["empType"] = v
    for e in members[8:20]:  # 숫자 칸으로 저장돼 앞자리 0이 빠진 휴대폰 → 확인 필요
        e["mobile"] = e["mobile"].replace("-", "")[1:]
    members[20]["hireDate"] = None  # 입사일 빈 칸 → 적재 불가
    counts["DATE_FORMAT"] = sum(isinstance(e[f], str) for e in employees for f in ("hireDate", "birthDate"))
    counts.update(ENUM_VALUE_SUGGESTED=8, INVALID_PHONE=12, REQUIRED=1)
    return counts, members[20]


def text(v):
    return v.isoformat() if isinstance(v, dt.date) else v


def write(path, headers, rows):
    wb = Workbook()
    ws = wb.active
    ws.append(headers)
    for r in rows:
        ws.append([text(v) for v in r])
    wb.save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("personas")
    ap.add_argument("out")
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")
    rng = random.Random(args.seed)
    personas = load_personas(args.personas, rng).head(SIZE * 4).to_dict("records")
    org, employees = build(personas, rng)
    counts, blocked = dirty(employees, rng)

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    name_of = {o["code"]: o["name"] for o in org}
    write(out / "조직.xlsx", ORG_HEADERS,
          [[o["code"], o["name"], o["parent"], o["type"], o["head"], i + 1] for i, o in enumerate(org)])
    keys = ["empNo", "name", "email", "dept", "position", "grade", "duty", "jobFamily", "jobKind", "jobRole", "empType",
            "hireDate", "birthDate", "gender", "mobile", "status", "resignDate"]
    write(out / "사원.xlsx", EMP_HEADERS, [[e[k] for k in keys] for e in employees])
    blocked["hireDate"] = "2019-03-04"
    write(out / "사원_적재불가0.xlsx", EMP_HEADERS, [[e[k] for k in keys] for e in employees])
    assert all(o["parent"] is None or o["parent"] in name_of for o in org)
    print("조직 %d행, 사원 %d행 → %s" % (len(org), len(employees), out))
    print("넣은 문제:", counts, "(적재 불가 행 사번 %s)" % blocked["empNo"])


if __name__ == "__main__":
    main()
