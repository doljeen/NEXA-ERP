"""합성 영수증 데이터셋 생성기 (MLOps 평가·최적화용).

정답(store/total/date)을 알고 있는 영수증 이미지를 만들어서
train / dev / test 로 나눠 저장한다. 실제 사람 라벨(run_feedback)이 쌓이기 전까지 쓰는 대체 데이터.

  python mlops/gen_receipts.py --n 120 --out mlops/data/receipts

결과:
  mlops/data/receipts/{train,dev,test}/r0001.jpg ...
  mlops/data/receipts/labels.jsonl   ← {"id","split","file","label":{store,total,date},"meta":{...}}
"""
import argparse
import json
import random
from datetime import date, timedelta
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ---------------------------------------------------------------- 폰트
# 윈도우(맑은 고딕) → 리눅스(Noto CJK) 순서로 찾음
FONT_CANDIDATES = [
    ("C:/Windows/Fonts/malgun.ttf", "C:/Windows/Fonts/malgunbd.ttf", 0),
    ("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 1),
]


def _find_fonts():
    for regular, bold, index in FONT_CANDIDATES:
        if Path(regular).exists():
            return regular, (bold if Path(bold).exists() else regular), index
    raise SystemExit("한글 폰트를 찾지 못했습니다. FONT_CANDIDATES에 경로를 추가하세요.")


REGULAR, BOLD, FONT_INDEX = _find_fonts()
_font_cache: dict = {}


def font(size: int, bold: bool = False):
    key = (size, bold)
    if key not in _font_cache:
        _font_cache[key] = ImageFont.truetype(BOLD if bold else REGULAR, size, index=FONT_INDEX)
    return _font_cache[key]


# ---------------------------------------------------------------- 재료
BRANDS = {
    "cafe": ["카페 온도", "커피나무", "블루빈", "달빛커피", "모닝브루", "카페 소담"],
    "mart": ["푸른마트", "이웃마트", "한빛마트", "새벽시장", "바른마트"],
    "conv": ["GS25", "CU", "세븐일레븐", "이마트24"],
    "food": ["김밥천국", "한솥도시락", "본죽", "명동칼국수", "고향순대국", "하나로식당"],
}
BRANCHES = ["역삼점", "선릉점", "강남역점", "삼성점", "판교점", "구미인동점", "광주첨단점", "부산서면점", ""]
ITEMS = {
    "cafe": [("아메리카노(ICE)", 4500), ("카페라떼", 5000), ("바닐라라떼", 5500), ("바닐라 스콘", 3800), ("치즈케이크", 6500), ("콜드브루", 5000)],
    "mart": [("생수 2L", 1900), ("컵라면 소컵", 1450), ("바나나 1송이", 4990), ("흰우유 900ml", 2980), ("계란 15구", 7330), ("두부 300g", 1800)],
    "conv": [("삼각김밥", 1500), ("바나나우유", 1700), ("컵커피", 2500), ("도시락", 4900), ("생수 500ml", 950), ("과자", 1800)],
    "food": [("김치찌개", 9000), ("제육볶음", 10000), ("칼국수", 9500), ("순대국", 10000), ("공기밥", 1000), ("만두", 6000)],
}
DATE_FORMATS = ["%Y-%m-%d", "%Y/%m/%d", "%Y.%m.%d", "%y-%m-%d", "%Y년 %m월 %d일"]
TOTAL_LABELS = ["합계", "총액", "결제금액", "받을금액", "총 결제금액", "합계금액"]
W = 576


def won(n: int, style: str) -> str:
    s = f"{n:,}"
    return {"plain": s, "won": s + "원", "symbol": "₩" + s}[style]


# ---------------------------------------------------------------- 영수증 내용
def make_receipt(rng: random.Random) -> tuple[list, dict, dict]:
    kind = rng.choice(list(BRANDS))
    store = f"{rng.choice(BRANDS[kind])} {rng.choice(BRANCHES)}".strip()
    day = date(2026, 9, 1) + timedelta(days=rng.randint(0, 63))
    hhmm = f"{rng.randint(7, 22):02d}:{rng.randint(0, 59):02d}"
    date_fmt = rng.choice(DATE_FORMATS)
    money = rng.choice(["plain", "plain", "won", "symbol"])

    items = []
    for name, price in rng.sample(ITEMS[kind], rng.randint(2, 5)):
        qty = rng.choice([1, 1, 1, 2, 3])
        items.append((name, qty, price * qty))
    subtotal = sum(amount for _, _, amount in items)
    # 할인 줄: 품목 합 ≠ 결제 금액이 되도록 (모델이 품목을 더하지 말고 '합계'를 읽게)
    discount = rng.choice([0, 0, 0, 500, 1000, round(subtotal * 0.1, -2)])
    total = int(subtotal - discount)
    supply = round(total / 1.1)
    vat = total - supply

    rows = []
    # 매장명 위치를 섞음: 첫 줄 / "매장명:" 줄 / [영수증] 머리글 아래
    header_style = rng.choice(["top", "top", "labeled", "titled"])
    if header_style == "titled":
        rows.append(("center", "[ 영 수 증 ]", 24, True))
    if header_style == "labeled":
        rows.append(("center", "영수증", 28, True))
        rows.append(("row", f"매장명: {store}", "", "", 20))
    else:
        rows.append(("center", store, 30, True))
    rows.append(("center", f"사업자번호 {rng.randint(100,999)}-{rng.randint(10,99)}-{rng.randint(10000,99999)}", 18))
    rows.append(("center", rng.choice(["서울특별시 강남구 테헤란로 000", "경기도 성남시 분당구 판교로 000", "경북 구미시 인동가산로 000", "부산 부산진구 중앙대로 000"]), 18))
    rows.append(("center", f"TEL 0{rng.randint(2, 70)}-000-{rng.randint(1000, 9999)}", 18))
    rows.append(("line",))
    rows.append(("row", f"{day.strftime(date_fmt)} {hhmm}", "", f"POS {rng.randint(1, 9):02d}", 19))
    rows.append(("line",))
    rows.append(("row", "품명", "수량", "금액"))
    for name, qty, amount in items:
        rows.append(("row", name, str(qty), won(amount, money)))
    rows.append(("line",))
    if discount:
        rows.append(("row", "할인", "", "-" + won(int(discount), money)))
    rows.append(("row", "과세물품가액", "", won(supply, money)))
    rows.append(("row", "부가세", "", won(vat, money)))
    rows.append(("line",))
    rows.append(("row", rng.choice(TOTAL_LABELS), "", won(total, money), 26, True))
    pay = rng.choice(["신용카드", "현금", "카카오페이"])
    rows.append(("row", pay, "", won(total, money)))
    if pay == "신용카드":
        rows.append(("row", "승인번호", "", f"{rng.randint(10**7, 10**8 - 1)}", 19))
    rows.append(("line",))
    rows.append(("center", rng.choice(["감사합니다", "또 방문해 주세요", "교환/환불은 7일 이내 영수증 지참"]), 20))

    label = {"store": store, "total": total, "date": day.isoformat()}
    meta = {"kind": kind, "date_fmt": date_fmt, "money": money, "header": header_style, "discount": int(discount)}
    return rows, label, meta


# ---------------------------------------------------------------- 그리기
def render(rows: list) -> Image.Image:
    img = Image.new("L", (W, 2400), 255)
    d = ImageDraw.Draw(img)
    y = 24
    for r in rows:
        kind = r[0]
        if kind == "center":
            ft = font(r[2], r[3] if len(r) > 3 else False)
            w = d.textlength(r[1], font=ft)
            d.text(((W - w) / 2, y), r[1], font=ft, fill=0)
            y += r[2] + 10
        elif kind == "line":
            d.text((20, y), "-" * 60, font=font(18), fill=0)
            y += 26
        elif kind == "row":  # (row, 왼쪽, 가운데, 오른쪽, 크기, 굵게)
            size = r[4] if len(r) > 4 else 21
            ft = font(size, r[5] if len(r) > 5 else False)
            d.text((24, y), r[1], font=ft, fill=0)
            if r[2]:
                w = d.textlength(r[2], font=ft)
                d.text((380 - w, y), r[2], font=ft, fill=0)
            if r[3]:
                w = d.textlength(r[3], font=ft)
                d.text((W - 24 - w, y), r[3], font=ft, fill=0)
            y += size + 12
    return img.crop((0, 0, W, y + 30))


def photo_effect(img: Image.Image, rng: random.Random) -> Image.Image:
    """휴대폰으로 찍은 느낌: 배경 + 기울기 + 조명 얼룩 + 노이즈 + 흐림"""
    pad = 80
    bg_tone = rng.randint(110, 180)
    bg = Image.new("L", (img.width + pad * 2, img.height + pad * 2), bg_tone)
    bg.paste(img, (pad, pad))
    bg = bg.rotate(rng.uniform(-3, 3), resample=Image.BICUBIC, expand=True, fillcolor=bg_tone)
    a = np.asarray(bg).astype(np.float32)
    h, w = a.shape
    start, end = rng.uniform(0.9, 1.0), rng.uniform(0.7, 0.9)
    grad = np.linspace(start, end, w)[None, :] * np.linspace(0.95, 1.0, h)[:, None]
    noise = np.random.default_rng(rng.randint(0, 10**9)).normal(0, rng.uniform(3, 9), a.shape)
    a = a * grad + noise
    out = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    return out.filter(ImageFilter.GaussianBlur(rng.uniform(0.4, 1.1)))


# ---------------------------------------------------------------- 실행
def main():
    p = argparse.ArgumentParser()
    p.add_argument("--n", type=int, default=120, help="전체 장수")
    p.add_argument("--out", type=Path, default=Path("mlops/data/receipts"))
    p.add_argument("--seed", type=int, default=42, help="같은 seed면 항상 같은 데이터셋")
    p.add_argument("--photo-ratio", type=float, default=0.5, help="사진 효과 비율")
    args = p.parse_args()

    rng = random.Random(args.seed)
    splits = ["train", "dev", "test"]
    # 1/3씩 나눔 (train은 DSPy 예시 뽑기, dev는 최적화 중 비교, test는 최종 승격 판단)
    split_of = [splits[i * 3 // args.n] for i in range(args.n)]

    args.out.mkdir(parents=True, exist_ok=True)
    for s in splits:
        (args.out / s).mkdir(exist_ok=True)

    with (args.out / "labels.jsonl").open("w", encoding="utf-8") as f:
        for i in range(args.n):
            rows, label, meta = make_receipt(rng)
            img = render(rows)
            photo = rng.random() < args.photo_ratio
            if photo:
                img = photo_effect(img, rng)
            meta["photo"] = photo
            rid = f"r{i + 1:04d}"
            rel = f"{split_of[i]}/{rid}.jpg"
            img.convert("RGB").save(args.out / rel, quality=rng.choice([75, 85, 92]))
            f.write(json.dumps({"id": rid, "split": split_of[i], "file": rel, "label": label, "meta": meta}, ensure_ascii=False) + "\n")

    counts = {s: split_of.count(s) for s in splits}
    print(f"생성 완료: {args.n}장 {counts} → {args.out}")


if __name__ == "__main__":
    main()