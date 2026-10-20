# datagen

이관 서비스 시연·평가용 가상 회사 데이터 생성기와 평가 스크립트. 서비스 빌드(Gradle)와 분리된 Python 도구다.

## 데이터 출처

[NVIDIA Nemotron-Personas-Korea](https://huggingface.co/datasets/nvidia/Nemotron-Personas-Korea) (CC BY 4.0)의 합성 인물을 직원으로 사용한다.
통계청(KOSIS) 인구 분포·대법원 이름 분포 기반의 합성 데이터라 실제 개인정보가 없다. 이름은 `persona` 문장("○○○ 씨는 …")에서 꺼낸다.

## 사용

```bash
pip install -r requirements.txt

# 1) 데이터셋 샤드 하나 내려받기 (약 220MB, 11만 명). 저장소 밖 별도 폴더에 둔다 (*.parquet은 커밋하지 않음)
curl -L -o ~/personas/personas.parquet https://huggingface.co/datasets/nvidia/Nemotron-Personas-Korea/resolve/main/data/train-00000-of-00009.parquet

# 2) 가상 회사 생성 → out/<회사>/조직.csv, 직원명부.xlsx|csv, 급여대장.xlsx …, answer_key.json
python -I generate.py ~/personas/personas.parquet out --seed 42

# 3) 이관 서비스(localhost:8087)에 올리고 정답지와 비교 → out/evaluation.md
python -I evaluate.py out

# (개발용) 수식과 함께 저장한 계산값이 엑셀 계산과 같은지 확인 → out/<회사>/<파일>.check.json
pip install formulas
python -I check_formulas.py out/mabaservice/급여대장.xlsx
```

| 파일 | 역할 |
|---|---|
| `generate.py` | 직원명부·조직 + 정답지. 오류를 넣기 전 명부를 복사해 두고 인사 엑셀은 그 명부로 만든다 |
| `hrfiles/facts.py` | 여러 파일이 같이 쓰는 사실: 연봉·월 기본급, 부양가족(합성 인물의 가구 형태 기반), 월별 근태 시간, 2026 공휴일 |
| `hrfiles/payroll.py` | 급여대장.xlsx |
| `hrfiles/xl.py` | 엑셀과 같은 결과를 내는 계산 함수(ROUND·ROUNDDOWN·VLOOKUP·오류값 전파) + 수식·계산값을 같이 쓰는 시트 도우미 |
| `evaluate.py` | 직원·조직 평가 + 인사 엑셀은 '미지원'으로 올려 서비스가 실제로 읽은 것을 기록 |
| `check_formulas.py` | 독립 구현(`formulas` 라이브러리)으로 다시 계산해 셀마다 비교 |

## 가상 회사

| 회사 | 업종 | 인원 | 형식 | 특징 |
|---|---|---|---|---|
| 가나테크 | IT | 120 | xlsx | 제목 행 + 빈 행, 한글 컬럼명 변형, 날짜 `2024.1.2` |
| 다라물산 | 제조 | 200 | CP949 CSV | `사번`·`입사일자`, 날짜 `20240102` |
| 마바서비스 | 서비스 | 80 | xlsx | 영문 컬럼명, 엑셀 날짜 셀과 문자 날짜 혼재 |

직업으로 부서를 정하고(개발·데이터 → 개발본부 …), 나이로 직위를 정한다. 팀마다 최연장 재직자가 팀장.

## 일부러 넣는 오류 (정답지에 기록)

| 유형 | 내용 | 이관 서비스 |
|---|---|---|
| DATE_FORMAT | 회사 기본 표기 + 8% 다른 표기 | FIX |
| REQUIRED | 입사일 누락 2건 | BLOCK |
| INVALID_EMAIL | `@` 빠짐 2건 | BLOCK |
| DUPLICATE_KEY | 사번 중복 1쌍 | BLOCK |
| INVALID_ENUM | 고용형태 "정규" 1건 | BLOCK |
| DATE_ORDER | 퇴사일 < 입사일 1건 | BLOCK |
| DUPLICATE_SUSPECTED | 이름·생년월일 같은 사람 1쌍 | WARN |
| POSITION_TYPO | 직위 오타 2건 ("과정") | **아직 못 잡음** (코드 매핑 필요) |

## 인사 엑셀 (ERP 도입 전 회사가 쓰던 파일)

ERP 도입 전 회사의 규칙은 문서가 아니라 **엑셀 수식 안에** 있다. 그래서 계산 셀은 모두 진짜 엑셀 수식으로 넣고,
정답지에 그 회사의 규칙과 일부러 넣은 사고 위치를 기록한다. 이관 서비스는 아직 이 파일들을 처리하지 못한다 (평가에서 '미지원').

### 수식 계산값 저장 방식

이관 서비스는 엑셀 셀의 **저장된 계산 결과**만 읽는다. openpyxl은 수식의 계산 결과를 저장하지 못하므로(빈 값으로 읽힘)
xlsxwriter로 **수식 + 파이썬으로 계산한 결과**를 같이 저장한다. 엑셀로 열면 다시 계산된다(fullCalcOnLoad).

- 파이썬 계산은 엑셀 의미를 따른다: ROUND는 0에서 먼 쪽으로 반올림(파이썬 `round`와 다름), 15자리 유효숫자, `#N/A`·`#REF!` 전파
- 맞는지는 `check_formulas.py`가 독립 구현인 `formulas` 라이브러리로 다시 계산해 셀마다 비교한다 (LibreOffice 재계산 대신)
  - 단, formulas의 SUMIFS는 숫자처럼 보이는 문자 조건("00101")을 처리하지 못해 검사기에서 단순 구현으로 바꿔 끼운다
- 확인 결과 (seed 42): 급여대장 3개 회사 수식 30,090셀 불일치 0

### 급여대장.xlsx

| | 가나테크 | 다라물산 | 마바서비스 |
|---|---|---|---|
| 시트 순서 | 설정, 간이세액표, 근태, 2026-07 ~ 12 | 2026-12 ~ 09(최근 달이 앞), 근태, 설정, 간이세액표 | 2026-10 ~ 12, TIME, SETTINGS, TAX_TABLE |
| 머리글 | 제목 2행 + 병합 묶음 헤더 + 컬럼 헤더, 합계 행 | 제목·귀속월 행 + 줄바꿈 헤더, 합계 행 | 영문 헤더 1행, 합계 없음 |
| 근태 연결 | `VLOOKUP(사번, 근태!…, FALSE)` (가로 배치) | `INDEX/MATCH` (가로 배치) | `SUMIFS(TIME!…, 사번, 월)` (세로 배치) |
| 요율 참조 | `설정!$B$4` 셀 참조 | `설정!$C$3` 셀 참조 | 이름 정의 `NPS_RATE` 등 |
| 통상시급 | `ROUNDDOWN((기본급+직책수당)/209,0)` | `ROUND((기본급+근속수당+직무수당)/209,0)` | `(기본급+직책수당)/209` 끝수 없음 |
| 가산율 | 수식에 숫자 1.5·0.5·2 | 수식에 숫자, **휴일 8h 초과도 1.5** | 이름 정의 OT_RATE 등 |
| 4대보험 기준·끝수 | 과세분(식대 제외), 10원 미만 버림 | **식대 포함**, 10원 단위 반올림 | 과세분, 원 미만 버림 |
| 소득세 | 간이세액표 VLOOKUP | 간이세액표 VLOOKUP | 간이세액표 × 원천징수비율(80/100/120%) |
| 기타 | | 근속수당 `MIN(DATEDIF(입사일,기준일,"Y"),10)×1만`, 사번이 숫자 셀(앞자리 0 없음) | |

일부러 넣은 사고 (위치·실제값·기대값은 정답지 `hrFiles["급여대장.xlsx"].accidents`):

| 유형 | 내용 | 회사 |
|---|---|---|
| STALE_RATE_LITERAL | 한 달 시트만 작년(2025) 요율을 수식에 숫자로 (국민연금 4.5%, 건강 3.545%, 장기요양 12.95%) | 3곳 |
| HARDCODED_EQUAL | 수식 대신 같은 금액을 숫자로 입력 (값은 맞지만 규칙이 없음) | 3곳 |
| HARDCODED_STALE | 수식 대신 지난달 값을 숫자로 (값이 틀림) | 가나, 다라 |
| SUM_RANGE_MISSING | 지급계·공제계 SUM 범위에서 한 열이 빠짐 (시트 전체 또는 아래쪽 절반) | 가나, 마바 |
| NEW_ROW_FORMULA_MISSING | 신규 입사자 행에 지급계·실지급 수식만 복사 | 가나 |
| ROW_AFTER_TOTAL | 합계 행 아래에 신규 입사자를 값으로만 추가 | 다라 |
| LOOKUP_NA | 근태 시트 사번이 숫자로 입력돼 VLOOKUP `#N/A` → 합계 행까지 전파 | 가나 |
| REF_ERROR | 참조 열 삭제로 `#REF!` → 합계 행까지 전파 | 다라 |
| TIME_ROW_MISSING | TIME 시트에 한 달 행이 없어 SUMIFS가 조용히 0 | 마바 |

값 변형: 이름 앞뒤 공백, 사번 숫자 셀(앞자리 0 소실), 줄바꿈 헤더, 2줄·병합 헤더, 헤더로 오인되는 제목 행.

### 사실 기준과 단순화

- 2026: 최저임금 10,320원, 국민연금 4.75%, 건강보험 3.595%, 장기요양 = 건강보험료 × 13.14%, 고용보험 0.9%, 비과세 식대 월 20만원
- 2025(사고용): 국민연금 4.5%, 건강보험 3.545%, 장기요양 12.95%
- **간이세액표는 실제 국세청 표가 아니다.** 근로소득공제·인적공제·기본세율·근로소득세액공제를 단순 적용해 5만원 구간, 가족 1~5명으로 만든 표다
- 4대보험 상·하한, 입사·퇴사 월 일할 계산, 비과세 한도 외 다른 비과세 항목은 넣지 않았다
- 공휴일은 생성기 내장 목록(2026, 대체공휴일·지방선거일 포함)이다

### 정답지 (`answer_key.json`의 `hrFiles`)

```jsonc
"hrFiles": {
  "급여대장.xlsx": {
    "kind": "PAYROLL", "closestEntity": "PAY_RESULT_HISTORY", "supported": false,
    "sheetOrder": ["설정", "간이세액표", "근태", "2026-07", ...],
    "sheets": {"2026-07": {"role": "monthlyPayroll", "headerRows": [3, 4], "firstDataRow": 5, "lastDataRow": 115,
                           "totalRow": 116, "rowOf": {"00146": 5, ...}, "formulas": {"hourly": "=ROUNDDOWN((F5+G5)/설정!$B$9,0)", ...}},
               "설정": {"role": "settings", "values": {...}, "cells": {...}}, ...},
    "expectedMapping": {"monthlyPayroll": {"사번": "empNo", "통상시급": "hourly", ...}},
    "rules": {"ordinaryHourlyWage": {"expr": "floor((basePay+positionAllowance)/209)", ...},
              "mealInInsuranceBase": false, "insuranceRounding": "floor10", ...},   // 키는 회사마다 같고 값이 다르다
    "legalDeviations": [{"rule": "holidayPremium", "company": "...", "statutory": "..."}],
    "accidents": [{"id": "GANA-PAY-04", "type": "HARDCODED_STALE", "sheet": "2026-09", "cells": ["N95"], "empNo": "00215",
                   "values": {"연장수당": {"actual": 216735, "expected": 433470}}, "cascade": ["지급계", ...],
                   "legalDeviation": false, "detectable": false, "needs": ["FORMULA_READ", "HARDCODED_IN_FORMULA_COLUMN", ...]}],
    "valueNoise": [{"type": "EMPNO_NUMERIC", ...}]
  }
}
```

작년 요율 사고는 `accidents`(사고 위치)와 `legalDeviations`(법정 기준 비교) 양쪽에 기록된다.

`needs`는 그 사고를 잡는 데 필요한 이관 서비스 기능이다. `evaluate.py`가 이를 집계해 필요한 기능 목록을 만든다.

| needs | 뜻 |
|---|---|
| FORMULA_READ | 계산값만이 아니라 수식 문자열을 읽음 |
| HARDCODED_IN_FORMULA_COLUMN | 수식 열에 숫자가 직접 들어간 셀 찾기 |
| RECOMPUTE_CHECK | 추출한 규칙으로 다시 계산해 저장값과 비교(검산) |
| RULE_EXTRACTION | 열마다 수식에서 회사 규칙 추출 |
| RATE_TABLE_BY_PERIOD | 기간별 요율과 비교 (작년 요율 탐지) |
| LEGAL_RULE_COMPARE | 법정 기준과 비교 |
| ERROR_VALUE | `#REF!`·`#N/A` 셀 탐지 (지금은 빈 값으로 읽음) |
| CROSS_SHEET_LOOKUP | 시트 사이 조회 관계 따라가기 |
| CROSS_FILE_MATCH | 파일 사이 대조 (예: 급여 근태 ↔ 근태기록) |
| MULTI_SHEET | 여러 시트에서 필요한 시트 고르기 |
| TOTAL_ROW_SKIP | 합계 행을 데이터에서 빼기 |

## 평가에서 드러난 이관 서비스 약점 (서비스 코드는 고치지 않고 목록만 남김)

| 약점 | 근거 (seed 42 평가) |
|---|---|
| 첫 번째 시트만 읽음 | 가나테크 급여대장은 `설정` 시트(9행)만 읽힘 |
| '값이 2칸 이상인 첫 행 = 헤더' | 다라물산 급여대장 2행(`귀속: 2026년 12월`, `근속 기준일`, 날짜)이 헤더로 잡혀 컬럼이 `컬럼2…`가 됨 |
| 합계 행·합계 아래 행을 데이터로 읽음 | 다라물산 `2026-12` 시트 195행 = 실제 헤더 1 + 직원 192 + 합계 1 + 합계 아래 1 |
| 오류 셀(`#N/A`·`#REF!`)을 빈 값으로 읽음 | `TableReader.cellText`가 ERROR → null. 수식 오류와 '원래 빈 칸'이 구분되지 않음 |
| 수식 문자열을 읽지 않음 | 계산값은 정상으로 읽힘(마바 `HOURLY`·`NET_PAY` 숫자, 빈 값 0%). 하지만 숫자 직접 입력·작년 요율·SUM 범위 누락은 값만으로 구분 불가 |
| 급여·연차 등 entity 스키마 없음 | `PAY_RESULT_HISTORY` 업로드 → `SCHEMA_NOT_READY` 경고만 |

## 주의

생성기의 오류 유형과 이관 서비스의 검증 규칙을 같은 사람이 만들었기 때문에, 검출률 100%는 **규칙이 의도대로 동작한다는 회귀 확인**이지 독립적인 정확도 측정이 아니다.
정확도를 주장하려면 생성 규칙을 모르는 사람이 만든 데이터나 실제 회사 양식 샘플로 따로 재야 한다.
