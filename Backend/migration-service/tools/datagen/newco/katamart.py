"""
카타마트 — 150명 유통(직영 6개 점포 + 본사 + 물류센터). 직원 대부분이 시급제 단시간 근로자(파트타임).

파일: 직원명부.csv(CP949, 이메일 열 없음, 재직 Y/N, 시급에 전각 숫자·쉼표), 조직.csv(CP949),
     급여대장.xlsx(11월·12월 시트, 점포별 소계 행이 중간에, 정규직은 기본급 값·시급 수식 / 파트타임은 시급 값·기본급 수식),
     근무시간표_12월.xlsx(점포별 시트, 날짜가 열인 가로 배치, 2줄 헤더(일자·요일)).
급여 규칙: 파트타임 기본급 = 시급 × 근무시간, 주휴수당(주 15시간 이상, 주 시간/40 × 8), 공휴일 근무 1.5배,
         월 60시간 미만 단시간 근로자는 4대보험 제외, 10원 단위 반올림, 소득세는 전원 1인 기준.
"""
import datetime as dt

from xlsxwriter.utility import xl_col_to_name

from hrfiles.common import Accidents
from hrfiles.facts import HOLIDAYS_2026
from hrfiles.payroll import TAX_ROWS
from hrfiles.xl import ROUND, ROUNDDOWN, Book, vlookup_approx

from . import base as B

CID, NAME = "katamart", "카타마트"
PAY_FILE, TS_FILE = "급여대장.xlsx", "근무시간표_12월.xlsx"
STORES = [("강남점", 26), ("잠실점", 24), ("분당점", 22), ("수원점", 20), ("인천점", 22), ("일산점", 18)]
HQ = [("경영지원팀", 5), ("영업관리팀", 7), ("물류센터", 6)]
MONTHS = {"11월": (2026, 11), "12월": (2026, 12)}
WAGE_2026 = B.MIN_WAGE[2026]
DOW = "월화수목금토일"


def people(df, used, seed):
    rng = B.rng_for(CID, "people", seed)
    total = sum(n for _, n in STORES + HQ)
    w = lambda o: 3.0 if B.has(o, "판매", "계산", "매장", "상품", "진열", "물류", "배송", "서비스") else 1.0  # noqa: E731
    picked = B.pick(df, used, total, rng, CID, w)
    picked.sort(key=lambda p: p["uuid"])
    rng.shuffle(picked)
    emps, k = [], 0
    for dept, n in STORES + HQ:
        group = sorted(picked[k:k + n], key=lambda p: -p["age"])
        k += n
        for j, p in enumerate(group):
            store = dept.endswith("점")
            if store:
                role = ("점장", "정규직", "점장") if j == 0 else ("부점장", "정규직", "부점장") if j == 1 else \
                    ("사원", "정규직", rng.choice(["진열", "농산", "수산"])) if j < 4 else ("파트너", "파트타임", rng.choice(["캐셔", "캐셔", "진열", "시식"]))
            elif dept == "물류센터":
                role = ("사원", "계약직", "입출고")
            else:
                role = ("팀장" if j == 0 else "사원", "정규직", "본사")
            pos, et, job = role
            age = int(p["age"])
            birth = B.birth_of(age, rng)
            if et == "파트타임":
                hire = dt.date(2026, 10, 31) - dt.timedelta(days=rng.randint(30, 900))
            else:
                hire = dt.date(birth.year + rng.randint(22, min(age, 40)), rng.randint(1, 12), rng.randint(1, 28))
                hire = min(hire, dt.date(2026, 9, 1))
            resign = None
            if et == "파트타임" and rng.random() < 0.12:
                resign = dt.date(2026, rng.randint(3, 10), rng.randint(1, 28))
                resign = max(resign, hire + dt.timedelta(days=40))
                resign = resign if resign < dt.date(2026, 11, 1) else None
            weekly = rng.choice([12, 15, 18, 20, 24, 25, 30, 35]) if et == "파트타임" else 40
            emps.append({"name": p["name"], "birthDate": birth, "sex": p["sex"], "dept": dept, "position": pos, "empType": et,
                         "job": job, "hireDate": hire, "resignDate": resign, "weekly": weekly, "mobile": B.phone(rng),
                         "hourly": rng.choice([WAGE_2026] * 6 + [10_500, 11_000]) if et == "파트타임" else None,
                         "monthly": {"점장": 3_900_000, "부점장": 3_300_000, "팀장": 4_200_000}.get(pos, 2_700_000) + rng.randint(0, 20) * 10_000
                         if et != "파트타임" else None,
                         "closer": et == "파트타임" and rng.random() < 0.3})
    emps.sort(key=lambda e: (e["hireDate"], e["name"]))
    for n, e in enumerate(emps, 1):
        e["empNo"] = "K%04d" % n
    return emps


# ================================================================ 근무 일정 (11·12월)

def days_of(y, m):
    d = dt.date(y, m, 1)
    out = []
    while d.month == m:
        out.append(d)
        d += dt.timedelta(days=1)
    return out


def schedule(e, rng):
    """날짜 → (시간, 야간시간). 파트타임은 주 계약시간을 요일 고정 근무로, 정규직은 0 (월급제)"""
    out = {}
    if e["empType"] != "파트타임":
        return out
    per_day = rng.choice([4, 5, 6]) if e["weekly"] >= 18 else rng.choice([3, 4])
    ndays = max(2, min(6, round(e["weekly"] / per_day)))
    dows = sorted(rng.sample(range(7), ndays))
    for ym in MONTHS.values():
        for d in days_of(*ym):
            if d < e["hireDate"] or (e["resignDate"] and d > e["resignDate"]):
                continue
            if d.weekday() in dows and rng.random() > 0.05:
                h = per_day + rng.choice([0, 0, 0, 0.5, -0.5, 1])
                out[d] = (h, min(2, h) if e["closer"] else 0)  # 마감조 18:00~24:00 → 22시 이후 2시간 야간
    return out


def weekly_holiday_hours(sched, y, m):
    """주휴시간: 월요일~일요일 주 중 일요일이 이 달에 있는 주, 이 달 날짜만으로 주 15시간 이상이면 주 시간/40×8 (최대 8)"""
    total = 0.0
    for d in days_of(y, m):
        if d.weekday() != 6:
            continue
        wk = sum(sched.get(d - dt.timedelta(days=i), (0, 0))[0] for i in range(7) if (d - dt.timedelta(days=i)).month == m)
        if wk >= 15:
            total += min(wk, 40) / 40 * 8
    return round(total, 2)


def month_hours(e, sched, y, m):
    if e["empType"] != "파트타임":
        hol = 8 if (y, m) == (2026, 12) and e.get("xmas") else 0
        return {"work": 209, "wh": 0, "night": 0, "hol": hol}
    days = [d for d in days_of(y, m) if d in sched]
    hol = sum(sched[d][0] for d in days if d in HOLIDAYS_2026)
    return {"work": sum(sched[d][0] for d in days) - hol, "wh": weekly_holiday_hours(sched, y, m),
            "night": sum(sched[d][1] for d in days), "hol": hol}


# ================================================================ 급여대장

SET = [("np", "국민연금", B.RATES_2026["np"], "pct"), ("hi", "건강보험", B.RATES_2026["hi"], "pct"),
       ("ltc", "장기요양", B.RATES_2026["ltc"], "pct"), ("ei", "고용보험", B.RATES_2026["ei"], "pct"),
       ("meal", "식대 비과세 한도", B.NONTAX_LIMIT, "won"), ("npFloor", "국민연금 하한", B.NP_FLOOR, "won"),
       ("npCap", "국민연금 상한", B.NP_CAP, "won"), ("minWage", "최저시급(2026)", WAGE_2026, "won"), ("exempt", "4대보험 제외 월 시간 미만", 60, None)]
SROW = {k: 2 + i for i, (k, *_r) in enumerate(SET)}
SVAL = {k: v for k, _l, v, _f in SET}


def SR(k):
    return "기준!$B$%d" % SROW[k]


COLS = [("empNo", "사원번호"), ("name", "성명"), ("dept", "소속"), ("empType", "근로형태"), ("hourly", "시급"), ("work", "근무시간"),
        ("wh", "주휴시간"), ("night", "야간시간"), ("hol", "휴일시간"), ("basePay", "기본급"), ("whPay", "주휴수당"),
        ("nightPay", "야간수당"), ("holPay", "휴일수당"), ("meal", "식대"), ("gross", "지급계"), ("np", "국민연금"),
        ("hi", "건강보험"), ("ltc", "장기요양"), ("ei", "고용보험"), ("tax", "소득세"), ("ltax", "지방소득세"), ("ded", "공제계"),
        ("net", "실지급액"), ("memo", "비고")]
L = {k: xl_col_to_name(i) for i, (k, _) in enumerate(COLS)}
SUMMED = [k for k, _ in COLS[COLS.index(("work", "근무시간")):-1]]


def calc(v):
    """v: 입력값 → 계산값. 엑셀 수식과 같은 순서·끝수"""
    v = dict(v)
    part = v["empType"] == "파트타임"
    if part:
        v["basePay"] = ROUND(v["hourly"] * v["work"], 0)
    else:
        v["hourly"] = ROUND(v["basePay"] / 209, 0)
    v["whPay"] = ROUND(v["hourly"] * v["wh"], 0)
    v["nightPay"] = ROUND(v["hourly"] * v["night"] * 0.5, 0)
    v["holPay"] = ROUND(v["hourly"] * v["hol"] * 1.5, 0)
    v["gross"] = v["basePay"] + v["whPay"] + v["nightPay"] + v["holPay"] + v["meal"]
    base = v["gross"] - min(v["meal"], SVAL["meal"])
    exempt = v["work"] < SVAL["exempt"]
    v["np"] = 0 if exempt else ROUND(min(max(base, SVAL["npFloor"]), SVAL["npCap"]) * SVAL["np"], -1)
    v["hi"] = 0 if exempt else ROUND(base * SVAL["hi"], -1)
    v["ltc"] = ROUND(v["hi"] * SVAL["ltc"], -1)
    v["ei"] = 0 if exempt else ROUND(base * SVAL["ei"], -1)
    v["tax"] = 0 if base < TAX_ROWS[0][0] else vlookup_approx(base, TAX_ROWS, 3)
    v["ltax"] = ROUNDDOWN(v["tax"] * 0.1, -1)
    v["ded"] = v["np"] + v["hi"] + v["ltc"] + v["ei"] + v["tax"] + v["ltax"]
    v["net"] = v["gross"] - v["ded"]
    return v


def formula(k, r, part):
    R = lambda c: "%s%d" % (L[c], r)  # noqa: E731
    base = "(%s-MIN(%s,%s))" % (R("gross"), R("meal"), SR("meal"))
    return {
        "hourly": None if part else "=ROUND(%s/209,0)" % R("basePay"),
        "basePay": "=ROUND(%s*%s,0)" % (R("hourly"), R("work")) if part else None,
        "whPay": "=ROUND(%s*%s,0)" % (R("hourly"), R("wh")),
        "nightPay": "=ROUND(%s*%s*0.5,0)" % (R("hourly"), R("night")),
        "holPay": "=ROUND(%s*%s*1.5,0)" % (R("hourly"), R("hol")),
        "gross": "=SUM(%s:%s)" % (R("basePay"), R("meal")),
        "np": "=IF(%s<%s,0,ROUND(MIN(MAX(%s,%s),%s)*%s,-1))" % (R("work"), SR("exempt"), base, SR("npFloor"), SR("npCap"), SR("np")),
        "hi": "=IF(%s<%s,0,ROUND(%s*%s,-1))" % (R("work"), SR("exempt"), base, SR("hi")),
        "ltc": "=ROUND(%s*%s,-1)" % (R("hi"), SR("ltc")),
        "ei": "=IF(%s<%s,0,ROUND(%s*%s,-1))" % (R("work"), SR("exempt"), base, SR("ei")),
        "tax": "=IF(%s<%d,0,VLOOKUP(%s,세액표!$A$2:$G$%d,3,TRUE))" % (base, TAX_ROWS[0][0], base, 1 + len(TAX_ROWS)),
        "ltax": "=ROUNDDOWN(%s*0.1,-1)" % R("tax"),
        "ded": "=SUM(%s:%s)" % (R("np"), R("ltax")),
        "net": "=%s-%s" % (R("gross"), R("ded")),
    }.get(k)


def _r(x):
    return round(x, 6) if isinstance(x, float) else x  # 0.5시간·소수 주휴시간 합계의 부동소수 꼬리 정리 (엑셀 15자리 표시와 같게)


def write_payroll(path, emps, hours, acc):
    A = Accidents("KATA-PAY", path.name)
    book = Book(path)
    sheets = {m: book.sheet(m) for m in MONTHS}
    s_set, s_tax = book.sheet("기준"), book.sheet("세액표")
    s_set.put("A", 1, "항목", "head")
    s_set.put("B", 1, "값", "head")
    for k, label, v, fmt in SET:
        s_set.put("A", SROW[k], label)
        s_set.put("B", SROW[k], v, fmt)
    s_set.widths({"A": 26, "B": 12})
    for c, h in zip("ABCDEFG", ["이상", "미만", "1인", "2인", "3인", "4인", "5인↑"]):
        s_tax.put(c, 1, h, "head")
    for n, row in enumerate(TAX_ROWS):
        for c, v in zip("ABCDEFG", row):
            s_tax.put(c, 2 + n, v, "won")
    meta, values = {}, {}
    order = [s for s, _ in STORES] + [d for d, _ in HQ]
    for mname, (y, m) in MONTHS.items():
        sh = sheets[mname]
        sh.put("A", 1, "카타마트 %d년 %d월 급여대장 (월급제·시급제)" % (y, m), "title")
        sh.put("A", 2, "작성: 경영지원팀 / 지급일 %d-%02d-10" % (y + (m == 12), m % 12 + 1))
        for k, h in COLS:
            sh.put(L[k], 3, h, "head")
        sh.ws.freeze_panes(3, 2)
        r = 4
        row_of, vals, subtotal, after_sub = {}, {}, [], {}
        for dept in order:
            members = [e for e in emps if e["dept"] == dept and e["empNo"] in hours[mname]]
            outside = [e for e in members if acc["outsideSubtotal"] == (mname, e["empNo"])]
            first = r
            for e in [x for x in members if x not in outside] + ["SUBTOTAL"] + outside:
                if e == "SUBTOTAL":
                    sh.put(L["name"], r, dept + " 소계", "totalLabel")
                    inside = [x for x in members if x not in outside]
                    for k in SUMMED:
                        sh.formula(L[k], r, "=SUM(%s%d:%s%d)" % (L[k], first, L[k], r - 1),
                                   _r(sum(vals[x["empNo"]]["actual"][k] for x in inside)), "total")
                    subtotal.append(r)
                    r += 1
                    continue
                h = hours[mname][e["empNo"]]
                part = e["empType"] == "파트타임"
                inp = {"empNo": e["empNo"], "name": e["name"], "dept": e["dept"], "empType": e["empType"],
                       "hourly": e["hourly"] if part else None, "basePay": None if part else e["monthly"], "meal": 0 if part else 200_000,
                       **{k: h[k] for k in ("work", "wh", "night", "hol")}, "memo": None}
                ideal_inp = dict(inp)
                if part and e["empNo"] in acc["staleWage"]:
                    inp["hourly"] = B.MIN_WAGE[2025]
                    ideal_inp["hourly"] = WAGE_2026
                over = acc["rowOver"].get((mname, e["empNo"]), {})
                if "inputs" in over:
                    inp.update(over["inputs"])
                actual, ideal = calc(inp), calc(ideal_inp)
                for k in over.get("hardZero", []):
                    actual[k] = 0
                if over.get("hardZero"):
                    actual["ded"] = sum(actual[k] for k in ("np", "hi", "ltc", "ei", "tax", "ltax"))
                    actual["net"] = actual["gross"] - actual["ded"]
                if e in outside:
                    actual["memo"] = "12/15 신규"
                for k, _ in COLS:
                    f = formula(k, r, part)
                    if f and k not in over.get("hardZero", []):
                        sh.formula(L[k], r, f, actual[k], None if k in ("work", "wh", "night", "hol") else "won")
                    else:
                        x = actual[k]
                        sh.put(L[k], r, x, "won" if isinstance(x, (int, float)) and k not in ("work", "wh", "night", "hol") and x >= 1000 else None)
                row_of[e["empNo"]] = r
                vals[e["empNo"]] = {"actual": actual, "ideal": ideal}
                if e in outside:
                    after_sub[e["empNo"]] = r
                r += 1
        total_row = r
        sh.put(L["name"], r, "총계", "totalLabel")
        for k in SUMMED:
            sh.formula(L[k], r, "=" + "+".join("%s%d" % (L[k], x) for x in subtotal),
                       _r(sum(vals[x]["actual"][k] for x in vals if x not in after_sub)), "total")
        sh.widths({L["name"]: 9, L["memo"]: 14})
        meta[mname] = {"role": "monthlyPayroll", "payMonth": "%d-%02d" % (y, m), "headerRows": [3], "firstDataRow": 4,
                       "lastDataRow": total_row - 1, "subtotalRows": subtotal, "totalRow": total_row, "titleRows": [1, 2],
                       "rowsOutsideSubtotal": sorted(after_sub.values()), "rowOf": row_of}
        values[mname] = vals
    book.close()

    # ------------ 정답지 사고
    hdr = dict(COLS)
    aff = [{"sheet": mn, "rowNo": meta[mn]["rowOf"][x], "empNo": x} for mn in MONTHS for x in acc["staleWage"] if x in meta[mn]["rowOf"]]
    A.add("MIN_WAGE_STALE", "11월", "파트타임 %d명 시급이 2025년 최저임금 10,030원 그대로 (2026년 10,320원) — 11·12월 모두" % len(acc["staleWage"]),
          ["LEGAL_RULE_COMPARE", "RATE_TABLE_BY_PERIOD"], legal=True, empNos=sorted(acc["staleWage"]), affected=aff,
          cells=["%s%d" % (L["hourly"], x["rowNo"]) for x in aff],
          values={"시급": {"actual": B.MIN_WAGE[2025], "expected": WAGE_2026}})
    for (mn, emp), over in sorted(acc["rowOver"].items()):
        r = meta[mn]["rowOf"][emp]
        v = values[mn][emp]
        if over["type"] == "WEEKLY_HOLIDAY_PAY_MISSING":
            A.add(over["type"], mn, "주 15시간 이상(계약 주 %d시간) 근무인데 주휴시간을 0으로 입력 → 주휴수당 %s원 누락"
                  % (over["weekly"], format(v["ideal"]["whPay"], ",")), ["LEGAL_RULE_COMPARE", "CROSS_FILE_MATCH"], legal=True,
                  empNo=emp, rowNo=r, cells=["%s%d" % (L["wh"], r)],
                  values={"주휴시간": {"actual": 0, "expected": v["ideal"]["wh"]}, "주휴수당": {"actual": 0, "expected": v["ideal"]["whPay"]}})
        elif over["type"] == "INSURANCE_EXEMPTION_WRONG":
            A.add(over["type"], mn, "월 %.1f시간(60시간 이상) 근무인데 4대보험 칸을 수식 대신 0으로 입력 (전월 기준 '단시간 제외' 그대로)" % v["actual"]["work"],
                  ["HARDCODED_IN_FORMULA_COLUMN", "FORMULA_READ", "LEGAL_RULE_COMPARE"], legal=True, empNo=emp, rowNo=r,
                  cells=["%s%d" % (L[k], r) for k in over["hardZero"]],
                  values={hdr[k]: {"actual": 0, "expected": v["ideal"][k]} for k in over["hardZero"]})
        elif over["type"] == "HOLIDAY_PREMIUM_MISSING":
            pass  # 아래에서 점포 단위로 한 건
    hp = [(mn, emp) for (mn, emp), o in acc["rowOver"].items() if o["type"] == "HOLIDAY_PREMIUM_MISSING"]
    if hp:
        rows = sorted(meta[mn]["rowOf"][emp] for mn, emp in hp)
        A.add("HOLIDAY_PREMIUM_MISSING", hp[0][0], "%s: 12/25(성탄절) 근무를 휴일시간이 아닌 근무시간에 넣어 휴일가산(0.5배분) 누락 — %d명"
              % (acc["holidayStore"], len(hp)), ["HOLIDAY_CALENDAR", "CROSS_FILE_MATCH", "LEGAL_RULE_COMPARE"], legal=True,
              rows=rows, empNos=sorted(e for _, e in hp), cells=["%s%d" % (L["hol"], x) for x in rows])
    mn, emp = acc["outsideSubtotal"]
    r = meta[mn]["rowOf"][emp]
    A.add("ROW_OUTSIDE_SUBTOTAL", mn, "12/15 신규 파트타이머 행을 점포 소계 아래에 추가 → 점포 소계·총계에서 빠짐", ["TOTAL_ROW_SKIP", "RECOMPUTE_CHECK"],
          empNo=emp, rowNo=r, affected=[{"sheet": mn, "rowNo": r}, {"sheet": mn, "rowNo": meta[mn]["totalRow"]}])
    key = {
        "file": path.name, "kind": "PAYROLL", "closestEntity": "PAY_RESULT_HISTORY", "supported": False,
        "sheetOrder": list(MONTHS) + ["기준", "세액표"],
        "sheets": {**meta, "기준": {"role": "settings", "values": SVAL, "cells": {k: SR(k) for k in SVAL}},
                   "세액표": {"role": "lookupTable", "note": "단순화한 간이세액표 (실제 국세청 표 아님)"}},
        "expectedMapping": {"monthlyPayroll": {h: k for k, h in COLS}},
        "rules": {
            "hourlyWorkers": "파트타임: 기본급 = ROUND(시급×근무시간), 시급은 값 / 정규직: 시급 = ROUND(기본급/209), 기본급은 값",
            "weeklyHolidayPay": "주 15시간 이상이면 주휴 = min(주 시간,40)/40×8 (월 안의 날짜만으로 주 계산, 일요일이 그 달인 주)",
            "nightPremium": {"rate": 0.5, "window": "22:00~06:00, 마감조 하루 2시간"},
            "holidayPremium": {"rate": 1.5, "days": "공휴일(12/25)"},
            "shortTimeExemption": "월 근무시간 60시간 미만이면 국민연금·건강·고용보험 0",
            "insuranceRounding": "round10 (10원 단위 반올림)", "localTax": "floor10",
            "incomeTax": "간이세액표 1인 열 고정 (부양가족 신고를 받지 않음)", "mealInInsuranceBase": False,
        },
        "legalDeviations": [{"rule": "minimumWage", "company": "시급 10,030원 %d명" % len(acc["staleWage"]), "statutory": "10,320원"}],
        "accidents": A.items,
        "valueNoise": [
            {"type": "MIXED_FORMULA_VALUE", "detail": "같은 열에서 정규직 행은 값, 파트타임 행은 수식 (기본급·시급). 회사 양식이라 오류 아님"},
            {"type": "SUBTOTAL_ROWS", "detail": "점포마다 '○○점 소계' 행이 데이터 중간에 있고 맨 아래 총계 = 소계의 합"},
            {"type": "SHEET_NAME_KOREAN_MONTH", "detail": "시트 이름이 '11월'·'12월' (연도 없음, 연도는 1행 제목에만)"},
        ],
        "crossFile": {"timesheet": {"file": TS_FILE, "detail": "12월 근무시간+휴일시간 = 근무시간표 점포 시트 합계 (근무시간표 사고 행·지원근무 제외)"}},
    }
    return key, meta


# ================================================================ 근무시간표 (가로 배치)

def write_timesheet(path, emps, scheds, acc):
    A = Accidents("KATA-TS", path.name)
    book = Book(path)
    days = days_of(2026, 12)
    meta = {}
    last_day_col = xl_col_to_name(2 + len(days))
    for store, _ in STORES:
        sh = book.sheet(store)
        sh.put("A", 1, "%s 2026년 12월 근무시간표 (단위: 시간)" % store, "title")
        sh.merge("A2:C2", "12월")
        for c, h in zip("ABC", ("사원번호", "성명", "구분")):
            sh.put(c, 3, h, "head")
        for i, d in enumerate(days):  # 2행 일자, 3행 요일 (컬럼 헤더 행에는 요일만 있어 날짜 열 이름이 겹침)
            c = xl_col_to_name(3 + i)
            sh.put(c, 2, d.day, "head")
            sh.put(c, 3, DOW[d.weekday()], "head")
        tot_c = xl_col_to_name(3 + len(days))
        sh.put(tot_c, 2, "월계", "head")
        sh.put(tot_c, 3, "합계", "head")
        members = [e for e in emps if e["dept"] == store and e["empType"] == "파트타임" and scheds[e["empNo"]]]
        support = [x for x in acc["support"] if x["to"] == store]
        r = 4
        row_of = {}
        for e in members + [x["emp"] for x in support]:
            sup = next((x for x in support if x["emp"] is e and e["dept"] != store), None)
            sh.put("A", r, e["empNo"], "text")
            sh.put("B", r, e["name"])
            sh.put("C", r, "타점지원" if sup else ("마감" if e["closer"] else "주간"))
            total = 0.0
            for i, d in enumerate(days):
                c = xl_col_to_name(3 + i)
                if sup:
                    h = scheds[e["empNo"]].get(sup["date"], (0, 0))[0] if d == sup["date"] else None
                else:
                    h = scheds[e["empNo"]].get(d, (None,))[0]
                if h is None:
                    continue
                if acc["textCell"] == (store, e["empNo"], d):
                    sh.put(c, r, "%sh" % (int(h) if float(h).is_integer() else h))
                    continue  # SUM이 문자를 건너뜀
                sh.put(c, r, h, "num")
                if not (store == acc["shortStore"] and d.day == 31):
                    total += h
            end = xl_col_to_name(1 + len(days)) if store == acc["shortStore"] else last_day_col
            sh.formula(tot_c, r, "=SUM(D%d:%s%d)" % (r, end, r), total, "num")
            row_of[e["empNo"] if not sup else e["empNo"] + "@지원"] = r
            r += 1
        sh.ws.freeze_panes(3, 3)
        sh.widths({"B": 8, "C": 8})
        for i in range(len(days)):
            sh.ws.set_column(3 + i, 3 + i, 4)
        meta[store] = {"role": "timesheetWide", "headerRows": [2, 3], "firstDataRow": 4, "lastDataRow": r - 1, "titleRows": [1],
                       "dayColumns": "D:%s (1~31일)" % last_day_col, "totalColumn": tot_c, "rowOf": row_of}
    book.close()
    st = acc["shortStore"]
    worked31 = [e["empNo"] for e in emps if e["dept"] == st and e["empType"] == "파트타임" and dt.date(2026, 12, 31) in scheds[e["empNo"]]]
    A.add("SUM_RANGE_MISSING", st, "%s 시트 합계 수식이 SUM(D:AG) — 30일까지만 (11월 양식 복사), 12/31 근무 %d명 시간 누락" % (st, len(worked31)),
          ["FORMULA_READ", "RECOMPUTE_CHECK", "MULTI_SHEET"], rows=sorted(meta[st]["rowOf"][x] for x in worked31),
          cells=["%s%d:%s%d" % (meta[st]["totalColumn"], 4, meta[st]["totalColumn"], meta[st]["lastDataRow"])],
          formula={"actual": "=SUM(D4:AG4)", "expected": "=SUM(D4:AH4)"})
    store, emp, d = acc["textCell"]
    r = meta[store]["rowOf"][emp]
    A.add("TEXT_NUMBER", store, "12/%d 근무시간을 '%sh' 문자로 입력 → 합계(SUM)가 건너뜀" % (d.day, scheds[emp][d][0]),
          ["VALUE_NORMALIZE", "RECOMPUTE_CHECK"], empNo=emp, rowNo=r, cells=["%s%d" % (xl_col_to_name(2 + d.day), r)])
    for x in acc["support"]:
        r1 = meta[x["emp"]["dept"]]["rowOf"][x["emp"]["empNo"]]
        r2 = meta[x["to"]]["rowOf"][x["emp"]["empNo"] + "@지원"]
        A.add("CROSS_SHEET_DUPLICATE", x["to"], "12/%d 타점 지원 근무를 원 소속(%s)과 지원 점포(%s) 시트에 모두 기록 → 같은 시간 두 번"
              % (x["date"].day, x["emp"]["dept"], x["to"]), ["DUPLICATE_DETECT", "MULTI_SHEET"], empNo=x["emp"]["empNo"], rowNo=r2,
              affected=[{"sheet": x["emp"]["dept"], "rowNo": r1}, {"sheet": x["to"], "rowNo": r2}])
    clean = [s for s, _ in STORES if s not in (st, store) and not any(x["to"] == s or x["emp"]["dept"] == s for x in acc["support"])]
    return {"file": path.name, "kind": "ATTENDANCE", "closestEntity": None, "supported": False, "period": "2026-12",
            "sheetOrder": [s for s, _ in STORES], "sheets": meta,
            "expectedMapping": {"timesheetWide": {"사원번호": "empNo", "성명": "name", "구분": "shiftType", "합계": "totalHours"}},
            "rules": {"layout": "가로 배치: 1~31일이 열, 2행 일자·3행 요일·사원번호 등 (2줄 헤더)", "total": "합계 = SUM(1일:31일)",
                      "scope": "파트타임만 (정규직은 월급제라 시간표 없음)"},
            "accidents": A.items, "cleanSheets": clean,
            "valueNoise": [{"type": "WIDE_LAYOUT", "detail": "날짜가 열 (세로로 풀면 사람×날짜)"},
                           {"type": "TWO_ROW_HEADER", "detail": "2행 일자(1~31)·3행 요일. 3행에는 날짜 열 이름이 요일뿐이라 같은 이름이 4~5번 겹침"}]}


# ================================================================ 직원명부·조직

ROSTER_COLS = [("empNo", "사원번호"), ("name", "성명"), ("dept", "소속"), ("jobRole", "직무"), ("position", "직위"),
               ("hireDate", "입사일"), ("birthDate", "생년월일"), ("empType", "근로형태"), ("_hourly", "시급"),
               ("_weekly", "주소정근로시간"), ("mobile", "연락처"), ("status", "재직"), ("resignDate", "퇴사일"), ("_memo", "비고")]


def write_roster(cdir, emps, acc, seed):
    rng = B.rng_for(CID, "roster", seed)
    errs = B.RosterErrors()
    rows = []
    for e in emps:
        wage = B.MIN_WAGE[2025] if e["empNo"] in acc["staleWage"] else e["hourly"]
        rows.append({"empNo": e["empNo"], "name": e["name"], "dept": e["dept"], "jobRole": e["job"], "position": e["position"],
                     "hireDate": e["hireDate"], "birthDate": e["birthDate"], "empType": e["empType"],
                     "_hourly": None if wage is None else B.won_text(wage, rng.choice(["plain", "plain", "comma", "full"])),
                     "_weekly": e["weekly"], "mobile": e["mobile"], "status": "N" if e["resignDate"] else "Y",
                     "resignDate": e["resignDate"], "_memo": "마감조" if e["closer"] else None, "_e": e})
    idx = [n for n in range(len(rows))]
    rng.shuffle(idx)
    take = lambda k, cond=lambda r: True: [idx.pop(idx.index(i)) for i in [j for j in idx if cond(rows[j])][:k]]  # noqa: E731
    for i in take(2, lambda r: r["status"] == "Y"):
        rows[i]["hireDate"] = None
        errs.add(i, "hireDate", "REQUIRED", "입사일 빈 칸")
    for i, bad in zip(take(2, lambda r: r["empType"] == "파트타임"), ["알바", "단기"]):
        rows[i]["empType"] = bad
        errs.add(i, "empType", "INVALID_ENUM", "근로형태 '%s' (코드표: 정규직·계약직·파트타임)" % bad)
    i, j = take(2, lambda r: r["empType"] == "파트타임")
    rows[j]["empNo"] = rows[i]["empNo"]
    errs.add(i, "empNo", "DUPLICATE_KEY", "사원번호 중복 (다른 사람)")
    errs.add(j, "empNo", "DUPLICATE_KEY", "사원번호 중복 — 이 행의 실제 사원번호는 %s" % rows[j]["_e"]["empNo"])
    for i in take(1, lambda r: r["status"] == "N" and r["hireDate"]):
        rows[i]["resignDate"] = rows[i]["hireDate"] - dt.timedelta(days=rng.randint(5, 60))
        errs.add(i, "resignDate", "DATE_ORDER", "퇴사일 < 입사일")
    # 퇴사했다가 다른 점포에서 다시 일하는 파트타이머: 새 사원번호, 예전 행은 재직 Y 그대로
    src = take(1, lambda r: r["status"] == "Y" and r["empType"] == "파트타임" and r["hireDate"])[0]
    old = dict(rows[src])
    old.update(empNo="P24%03d" % rng.randint(1, 199),  # 2025년 이전 시스템 사번 형식
               dept=rng.choice([s for s, _ in STORES if s != rows[src]["dept"]]), hireDate=dt.date(2024, 3, 4), _memo="재입사 전 근무")
    rows.append(old)
    errs.add(src, "name", "DUPLICATE_SUSPECTED", "같은 사람(이름·생년월일 같음)이 예전 사원번호 %s로도 재직 Y" % old["empNo"])
    errs.add(len(rows) - 1, "name", "DUPLICATE_SUSPECTED", "재입사 전 행이 퇴사 처리되지 않음 (새 사원번호 %s)" % rows[src]["empNo"])
    for r in rows:
        r["hireDate"] = B.fmt_date(r["hireDate"], B.date_style(rng, "dot", ["yy"], 0.1))
        r["birthDate"] = B.fmt_date(r["birthDate"], B.date_style(rng, "dot", ["yy"], 0.1))
        r["resignDate"] = B.fmt_date(r["resignDate"], "dot")
    B.date_format_errors(rows, ("hireDate", "birthDate", "resignDate"), errs)
    first = B.write_csv(cdir / "직원명부.csv", [h for _, h in ROSTER_COLS], [[r.get(f) for f, _ in ROSTER_COLS] for r in rows], "cp949")
    return {"mapping": {h: (None if f.startswith("_") else f) for f, h in ROSTER_COLS}, "errors": errs.rows(first), "rows": len(rows),
            "layout": {"encoding": "cp949", "firstDataRow": first,
                       "notes": ["이메일 열 없음", "재직 Y/N", "시급에 쉼표·전각 숫자 혼재 (값 변형)", "정규직은 시급 빈 칸, 주소정근로시간 40"]}}


def write_org(cdir):
    org = [{"deptCode": "KT000", "name": "카타마트", "parentDeptCode": None},
           {"deptCode": "KT100", "name": "본사", "parentDeptCode": "KT000"},
           {"deptCode": "KT200", "name": "영업본부", "parentDeptCode": "KT000"}]
    for n, (d, _) in enumerate(HQ, 1):
        org.append({"deptCode": "KT1%02d" % n, "name": d, "parentDeptCode": "KT100"})
    for n, (s, _) in enumerate(STORES, 1):
        org.append({"deptCode": "KT2%02d" % n, "name": s, "parentDeptCode": "KT200"})
    cols = [("deptCode", "점포코드"), ("name", "점포명"), ("parentDeptCode", "상위코드")]
    B.write_org(cdir / "조직.csv", org, cols, "cp949")
    return org, cols


def build(df, used, out_dir, seed):
    company = {"id": CID, "name": NAME, "industry": "유통",
               "profile": "150명 유통(직영 6개 점포), 시급제 파트타이머 다수, CP949 CSV 명부, 점포 소계가 끼어 있는 급여대장, 가로 배치 근무시간표"}
    cdir = out_dir / CID
    cdir.mkdir(parents=True, exist_ok=True)
    emps = people(df, used, seed)
    rng = B.rng_for(CID, "plan", seed)
    for e in emps:
        e["xmas"] = e["empType"] == "정규직" and e["dept"].endswith("점") and rng.random() < 0.5
    scheds = {e["empNo"]: schedule(e, rng) for e in emps}
    active = lambda e, y, m: e["hireDate"] <= dt.date(y, m, 28) and (e["resignDate"] is None or e["resignDate"] >= dt.date(y, m, 1))  # noqa: E731
    parts = [e for e in emps if e["empType"] == "파트타임" and e["resignDate"] is None and e["hireDate"] < dt.date(2026, 10, 1)]

    # 사고 계획
    stale = sorted(e["empNo"] for e in rng.sample([e for e in parts if e["hourly"] == WAGE_2026], 3))
    used_e = set(stale)
    pick = lambda cond: rng.choice([e for e in parts if e["empNo"] not in used_e and cond(e)])  # noqa: E731
    wh_e = pick(lambda e: e["weekly"] >= 18 and e["dept"] != "수원점")
    used_e.add(wh_e["empNo"])
    ins_e = min([e for e in parts if e["empNo"] not in used_e and e["dept"] != "수원점"
                 and month_hours(e, scheds[e["empNo"]], 2026, 12)["work"] >= 60],
                key=lambda e: (month_hours(e, scheds[e["empNo"]], 2026, 12)["work"], e["empNo"]))
    used_e.add(ins_e["empNo"])
    # 12/15 잠실점 신규 파트타이머 (소계 아래 행)
    newbie = next(e for e in emps if e["dept"] == "잠실점" and e["empType"] == "파트타임" and e["empNo"] not in used_e and e["resignDate"] is None)
    newbie["hireDate"] = dt.date(2026, 12, 15)
    scheds[newbie["empNo"]] = schedule(newbie, rng)
    used_e.add(newbie["empNo"])
    row_over = {("12월", wh_e["empNo"]): {"type": "WEEKLY_HOLIDAY_PAY_MISSING", "inputs": {"wh": 0}, "weekly": wh_e["weekly"]},
                ("12월", ins_e["empNo"]): {"type": "INSURANCE_EXEMPTION_WRONG", "hardZero": ["np", "hi", "ltc", "ei"]}}
    hol_store = "수원점"
    hours = {}
    for mname, (y, m) in MONTHS.items():
        hours[mname] = {}
        for e in emps:
            if not active(e, y, m) or (e["empType"] == "파트타임" and not any(d.month == m for d in scheds[e["empNo"]])):
                continue
            hours[mname][e["empNo"]] = month_hours(e, scheds[e["empNo"]], y, m)
            if mname == "12월" and e["dept"] == hol_store and hours[mname][e["empNo"]]["hol"] > 0:
                h = hours[mname][e["empNo"]]
                row_over[(mname, e["empNo"])] = {"type": "HOLIDAY_PREMIUM_MISSING", "inputs": {"work": h["work"] + h["hol"], "hol": 0}}
    # 근무시간표 사고: 인천점 합계 범위 30일까지, 분당점 문자 셀, 타점 지원 이중 기록
    text_e = rng.choice([e for e in parts if e["dept"] == "분당점" and e["empNo"] not in used_e and
                         any(d.month == 12 for d in scheds[e["empNo"]])])
    text_d = rng.choice(sorted(d for d in scheds[text_e["empNo"]] if d.month == 12))
    sup_e = rng.choice([e for e in parts if e["dept"] == "강남점" and e["empNo"] not in used_e and
                        any(d.month == 12 for d in scheds[e["empNo"]])])
    sup_d = rng.choice(sorted(d for d in scheds[sup_e["empNo"]] if d.month == 12 and d.day < 25))
    acc_ts = {"shortStore": "인천점", "textCell": ("분당점", text_e["empNo"], text_d),
              "support": [{"emp": sup_e, "to": "잠실점", "date": sup_d}]}
    acc_pay = {"staleWage": stale, "rowOver": row_over, "outsideSubtotal": ("12월", newbie["empNo"]), "holidayStore": hol_store}
    pay_key, _ = write_payroll(cdir / PAY_FILE, emps, hours, acc_pay)
    ts_key = write_timesheet(cdir / TS_FILE, emps, scheds, acc_ts)
    roster = write_roster(cdir, emps, acc_pay, seed)
    org, org_cols = write_org(cdir)
    return B.answer_key(company, "직원명부.csv", "조직.csv", roster["mapping"], org_cols, roster["errors"],
                        {"employees": len(emps), "rosterRows": roster["rows"], "orgs": len(org),
                         "partTimers": sum(e["empType"] == "파트타임" for e in emps)},
                        {PAY_FILE: pay_key, TS_FILE: ts_key}, {"rosterLayout": roster["layout"]})
