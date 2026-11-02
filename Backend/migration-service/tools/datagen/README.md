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
#    기존 3곳 + 추가 4곳(newco/: saalabs, jachaprec, katamart, pahagongdan). 약 30초
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
| `hrfiles/leave.py` | 연차관리대장.xlsx |
| `hrfiles/attendance.py` | 근태기록.xlsx, 연장근무신청대장.xlsx |
| `hrfiles/appointments.py` | 인사발령대장.xlsx |
| `hrfiles/salary.py` | 연봉이력.xlsx |
| `hrfiles/severance.py` | 퇴직금추계표.xlsx |
| `hrfiles/insurance.py` | 4대보험취득상실.xlsx, 부양가족.xlsx |
| `hrfiles/common.py` | 사고 목록·정답지 정리 도우미 |
| `hrfiles/xl.py` | 엑셀과 같은 결과를 내는 계산 함수(ROUND·ROUNDDOWN·VLOOKUP·오류값 전파) + 수식·계산값을 같이 쓰는 시트 도우미 |
| `evaluate.py` | 직원·조직 평가 + 인사 엑셀은 '미지원'으로 올려 서비스가 실제로 읽은 것을 기록 |
| `check_formulas.py` | 독립 구현(`formulas` 라이브러리)으로 다시 계산해 셀마다 비교 |
| `newco/` | 추가 가상 회사 4곳 (회사마다 모듈 하나, `base.py`는 공통 도우미. 아래 '추가 가상 회사'). `generate.py`가 기존 3곳 다음에 만든다 |

## 가상 회사

| 회사 | 업종 | 인원 | 형식 | 특징 |
|---|---|---|---|---|
| 가나테크 | IT | 120 | xlsx | 제목 행 + 빈 행, 한글 컬럼명 변형, 날짜 `2024.1.2` |
| 다라물산 | 제조 | 200 | CP949 CSV | `사번`·`입사일자`, 날짜 `20240102` |
| 마바서비스 | 서비스 | 80 | xlsx | 영문 컬럼명, 엑셀 날짜 셀과 문자 날짜 혼재 |
| 사아랩스 | IT 스타트업 | 15 | UTF-8 CSV만 | 구글 시트 내보내기, 수식 없음, 포괄임금제 (newco) |
| 자차정밀 | 제조 | 600 | xlsx | 주야 2교대, 부서별 급여 시트·조 소계·결재란·숨긴 열 (newco) |
| 카타마트 | 유통 | 150 | CP949 CSV + xlsx | 시급제 파트타이머 108명, 점포 소계, 가로 배치 근무시간표 (newco) |
| 파하공단 | 공공 | 250 | xlsx | 호봉제 봉급표, 공무원 연가일수표, 마스킹된 주민번호 열 (newco) |

직업으로 부서를 정하고(개발·데이터 → 개발본부 …), 나이로 직위를 정한다. 팀마다 최연장 재직자가 팀장. (기존 3곳 기준. 추가 4곳은 회사마다 다름)

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
- 확인 결과 (seed 42): 인사 엑셀 27개(회사 3 × 9종) 수식 85,869셀 불일치 0. 엑셀·LibreOffice로 직접 연 확인은 아님

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

### 연차관리대장.xlsx

기준일 2026-12-31. 재직자만. 사용 내역은 facts의 연차 사용일(근무일, 일부 반차)이라 근태기록과 날짜가 같다.

| | 가나테크 | 다라물산 | 마바서비스 |
|---|---|---|---|
| 시트 | 연차현황, 사용내역, 사용촉진 | 2026 연차대장, 연차사용, 촉진 | LEAVE_SUMMARY, LEAVE_USAGE, PROMOTION, CONFIG |
| 발생 기준 | 입사일 | **회계연도(1/1)**, 입사 다음 해는 `ROUND(15×전년 근무일/365,1)` | 입사일 |
| 발생 수식 | `IF(근속<1,MIN(월수,11),MIN(15+INT((근속-1)/2),25))` 법정대로 | 1년 이상 무조건 15 → **근속 가산 누락** (근로자에게 불리) | `15+INT((근속-1)/2)` → **25일 상한 없음** (법보다 유리) |
| 사용 집계 | `SUMIFS(일수)` | `COUNTIFS(구분="연차") + COUNTIFS(구분="반차")×0.5` | `SUMIFS(DAYS)` |
| 잔여 | 발생 − 사용 | 발생 − 사용 | 발생 + 이월(한도 5, 값 입력) − 사용 |
| 기준일 | 수식에 `DATE(2026,12,31)` | 수식에 `DATE(2026,12,31)` | 이름 정의 `BASE_DATE` |

| 사고 | 내용 | 회사 |
|---|---|---|
| USED_ON_HOLIDAY | 사용일이 주말·공휴일 | 3곳 |
| REMAIN_HARDCODED | 잔여를 11월 말 값으로 입력 → 잔여 ≠ 발생 − 사용 | 가나, 마바 |
| SUM_RANGE_SHORT | SUMIFS 범위가 마지막 5행(12월 말 추가분)을 빠뜨림 | 가나 |
| TEXT_NUMBER | 일수를 전각 문자 '１'로 → SUMIFS가 안 더함 | 가나 |
| KIND_TYPO | 구분 '연차 '(공백) → COUNTIFS가 안 셈 | 다라 |
| DUPLICATE_ROW | 같은 사람·같은 날 사용 기록 두 번 | 다라 |
| GRANTED_HARDCODED | 발생을 수식 대신 15로 입력 | 다라 |
| NEGATIVE_REMAIN | 회계연도 비례 발생보다 많이 사용 (규칙 차이의 결과) | 다라 |
| KEY_MISSING | 사번 없이 이름만 입력 → 집계 누락 | 마바 |
| CAP_EXCEEDED | 이월 8일 > 회사 한도 5일 | 마바 |
| DATE_ORDER / PROMOTION_MISSING | 2차 촉진일 < 1차 / 촉진 대상인데 기록 없음 | 가나 / 마바 |
| LEGAL_SENIORITY_MISSING / LEGAL_CAP_MISSING | 법정 공식과 다른 발생 수식 (`legalDeviations`에 대상 행) | 다라 / 마바 |

정답지 `employees`에 직원별 `granted`(회사 규칙), `statutory`(법정), `used`·`remain`(사고가 없었다면)을 둔다.
사용 촉진 시기는 달력 기준(7/1, 10/31)으로 단순화했다.

### 근태기록.xlsx / 연장근무신청대장.xlsx

기간은 전환 직전 한 달(2026-12, 월요일 시작 1~5주)이다. 급여대장 12월분과 맞추려고 '4주' 대신 달력상 한 달로 잡았다.
일별 연장(분)을 회사 규칙대로 끝수 처리하면 **급여대장 2026-12의 연장시간과 같고**, 휴일근무 분은 휴일·휴일8h초과 시간과 같다
(정답지 `crossFile.payroll.rows`, seed 42 기준 사고 당사자 1명 말고 모두 일치). 연차·반차 날짜는 연차관리대장 사용 내역과 같다.
야간 시간은 맞추지 않았다. 연장은 주별 여유(52시간 − 기본근무 − 휴일근무) 안에서 배분하고, 일부러 한 주에 몰아 52시간을 넘긴 사람만 따로 둔다.

| | 가나테크 | 다라물산 | 마바서비스 |
|---|---|---|---|
| 시트 | 일별근태(제목 행), 주간집계, 월집계 | 근태(사람별 정렬), 주별, 월별 | DAILY, WEEKLY, MONTHLY |
| 근무(분) | `ROUND((퇴근-출근)*1440,0)-휴게` | `IF(빈칸,0, ROUND(MOD(퇴근-출근,1)*1440,0)-휴게)` 자정 넘김 처리 | `ROUND((OUT-IN)*1440,0)-BREAK_MIN` |
| 연장 기준 | 18:00 이후 퇴근분 | 17:00 이후 퇴근분 (`퇴근+(퇴근<출근)`) | 하루 480분 초과분 (유연근무) |
| 연장 끝수 | **월 합산 후 10분 미만 버림** | 월 합산 후 시간 단위 반올림 (30분 미만 버림·이상 올림, 행정해석 허용) | **일별 15분 미만 버림** |
| 신청시간 | `ROUND((종료-시작)*24,1)` 시간 | `ROUND(MOD(종료-시작,1)*24,1)` 시간 | `ROUND((END-START)*1440,0)` 분 |

| 사고 | 내용 | 회사 |
|---|---|---|
| OUT_MISSING | 퇴근 누락: 가나는 근무(분) 음수, 다라는 빈 칸 처리 수식 때문에 조용히 0 | 가나, 다라 |
| IN_AFTER_OUT | 출근·퇴근 뒤바뀜 → 근무(분) 음수 | 가나, 마바 |
| LEAVE_CONFLICT | 연차관리대장에는 연차인데 근태는 정상 출근 | 가나, 마바 |
| DUPLICATE_ROW | 같은 날 출퇴근 두 번 → 연장 이중 집계, 급여대장과 어긋남 | 다라 |
| WEEKLY_52H_EXCEEDED | 주 52시간 초과 (일부러 넣은 것 + 연장·휴일이 많아 못 피한 것, 구분해 기록) | 3곳 |
| NO_APPLICATION | 근태에는 연장·휴일근무인데 신청 기록 없음 | 3곳 |
| REJECTED_BUT_WORKED | 반려된 날에 연장·휴일근무 | 3곳 |
| DATE_ORDER | 승인일 < 신청일 | 다라 |

### 인사발령대장.xlsx (수식 없음)

입사 → 승진(나이 기준 사다리, 해당 나이가 되는 해 1월 1일) → 전보(0~2회) → 보직(팀장) → 퇴사. 마지막 발령 상태 = 직원명부(오류 전)의 부서·직위.
일부 부서는 예전 이름이 있어(개발팀 → 플랫폼개발팀 등) 오래된 발령에는 옛 이름이 나온다.

| | 가나테크 | 다라물산 | 마바서비스 |
|---|---|---|---|
| 형식 | 발령 전·후 부서·직위, 날짜 셀 + 10% 문자 날짜 `2021.3.1` | **발령 후 상태만**, 날짜는 문자 `20210301`, 사번 숫자 | 영문, FROM/TO |
| 발령 구분 | 입사·승진·전보·보직·퇴사 | 신규입사·승진·전배·직책임명·퇴직 | HIRE·PROMOTION·TRANSFER·APPOINT·RESIGN |

사고(3곳 공통): 발령일 < 입사일, 같은 날 다른 부서로 전보 2건, 퇴사 후 발령, 마지막 승진 발령 누락(→ 직원명부 직위와 불일치).

### 연봉이력.xlsx

2026년 연봉 = facts 연봉, **2026 월 기본급 = 급여대장 기본급**(회사별 끝수 처리). 이전 연도는 2026년에서 거꾸로 만든다.

| | 가나테크 | 다라물산 | 마바서비스 |
|---|---|---|---|
| 형식 | 사람·연도별 행 | 사람·연도별 행 | 사람 1행, 연도별 열 (SAL_2021~2026) |
| 입력 / 수식 | 연봉 입력, 인상률 `ROUND(연봉/전년-1,4)`, 전년 연봉 `IF(위 행 사번 같으면 위 연봉)` | **인상률 입력, 연봉 = `ROUND(전년×(1+인상률),-3)`** (2026이 정확히 맞게 거꾸로 찾음) | 연봉 입력, RAISE 수식 |
| 월 기본급 | `ROUNDDOWN(연봉/12,-1)` | `ROUND(연봉/12,0)` | `ROUNDUP(SAL_2026/12,-2)` |

| 사고 | 내용 | 회사 |
|---|---|---|
| SALARY_NOT_UPDATED | 2026 연봉을 2025 금액 그대로 → 급여대장 기본급과 불일치 | 가나 |
| RATE_AMOUNT_MISMATCH | 인상률을 숫자로 입력, 금액과 안 맞음 | 가나, 마바 |
| AMOUNT_HARDCODED | 연봉 수식 대신 협상 금액 → 이후 연도 수식이 이어받아 2026까지 틀림 | 다라 |
| PERCENT_AS_NUMBER | 인상률 3.5%를 3.5로 → 연봉 4.5배, 이후 연도 모두 틀림 | 다라 |
| VALUE_MISSING | 재직자 SAL_2026 빈 칸 → 월 기본급 0 | 마바 |

### 퇴직금추계표.xlsx

2026-12-31 재직자. 퇴직금 = 1일 평균임금 × 30 × 근속일수/365. 10~12월 임금은 담당자가 급여대장 지급계를 **옮겨 적은 값**이라
급여대장 사고가 그대로 따라온다(`PAYROLL_ACCIDENT_CARRIED`). 정답지 `employees`에는 법정 공식(92일, 통상임금 하한, 1년 미만 0)으로 계산한 값.
상여·연차수당 가산은 넣지 않았다.

| | 가나테크 | 다라물산 | 마바서비스 |
|---|---|---|---|
| 근속일수 | `DATE(2026,12,31)-입사일+1` | 기준일 열(값) − 입사일 + 1, 근속연수 `ROUND(일수/365,2)`를 곱함 | 이름 정의 `BASE_DATE` − 입사일 + 1 |
| 평균임금 | `ROUND(3개월/92,2)` | `3개월/90` (**90일 고정**, 근로자에게 유리) | `TRUNC(3개월/92)` |
| 통상임금 하한 | **없음** (통상일급이 더 큰 행이 많음) | `MAX(평균, 통상)` | `MAX(평균, 통상)` |
| 1년 미만 | `IF(근속<365,0,…)` | 같음 | 같음 |

사고: 근속 기준일을 작년(2025-12-31)으로(가나 수식·다라 값), 1년 미만인데 IF 빠진 행(가나), 입사일 연도 오타·12월 칸에 11월 급여(마바).

### 4대보험취득상실.xlsx / 부양가족.xlsx

- 4대보험: 취득일 = 입사일, 상실일 = 퇴사일 + 1, 보수월액 `ROUNDDOWN(연봉/12,-3)`(다라물산은 **+ 식대 20만원**, 급여대장 규칙과 같음), 보험료는 2행 요율 칸 참조
  - 사고: 취득일 지연(입사일과 다름), 상실일 = 퇴사일, 퇴사자 상실일 누락, 연봉 미갱신(연봉이력과 다름)
- 부양가족: 명세(관계·생년월일·나이 수식·기본공제·경로우대 수식·비상연락처) + 집계(`1+COUNTIFS(사번, 기본공제=Y)`, 원천징수비율)
  - 집계 가족수 = 급여대장 공제가족수 (facts의 부양가족, 2026 귀속 기준 자녀 20세 이하·직계존속 60세 이상)
  - 사고: 나이 요건 안 되는데 기본공제 Y(자녀·부모), 11월 출생 자녀 추가했는데 급여대장 미반영, 원천징수비율 90%
  - 값 변형: 비상연락처 형식 5가지 혼재(`010-…`, `010…`, `010 …`, `+82-10-…`, `010.…`), 다라물산은 기본공제를 O/X로

### 사실 기준과 단순화

- 2026: 최저임금 10,320원, 국민연금 4.75%, 건강보험 3.595%, 장기요양 = 건강보험료 × 13.14%, 고용보험 0.9%, 비과세 식대 월 20만원
- 2025(사고용): 국민연금 4.5%, 건강보험 3.545%, 장기요양 12.95%
- **간이세액표는 실제 국세청 표가 아니다.** 근로소득공제·인적공제·기본세율·근로소득세액공제를 단순 적용해 5만원 구간, 가족 1~5명으로 만든 표다
- 4대보험 상·하한, 입사·퇴사 월 일할 계산, 비과세 한도 외 다른 비과세 항목은 넣지 않았다
- 공휴일은 생성기 내장 목록(2026, 대체공휴일·지방선거일 포함)이다

### 정답지 (`answer_key.json`의 `hrFiles`)

파일 9종이 같은 구조다 (아래 값은 형태를 보여주는 예시). 파일에 따라 `employees`(직원별 정답값)·`crossFile`(다른 파일과 맞춰 볼 항목)·`finalState` 등이 더 있다.

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
| HOLIDAY_CALENDAR | 공휴일·주말 달력과 대조 |
| VALUE_NORMALIZE | 전각 숫자·앞뒤 공백 같은 표기 차이 정리 |
| DUPLICATE_DETECT | 같은 내용의 중복 행 찾기 |
| CROSS_FIELD_RULE | 같은 행 안의 필드 관계 (날짜 순서, 설정 한도) |
| NAME_MATCH | 키 없이 이름으로 사람 찾기 |
| REQUIRED_PAIR | 출근·퇴근처럼 짝으로 있어야 하는 값의 누락 |
| REQUIRED | 필수 값 누락 |
| ENUM_CHECK | 허용 값 목록 검사 (원천징수비율 80·100·120) |

## 추가 가상 회사 (newco/)

기존 3곳은 검증 규칙을 만든 쪽이 같이 만들어 평가가 순환적이다. 그래서 **검증 규칙(`migration-service/src`)과 평가 결과를 보지 않고**,
실제 한국 회사의 인사·급여 엑셀이 어떻게 틀어지는지만 보고 4곳을 더 만들었다. 규모·업종·파일 형식·급여 규칙·사고 유형을 기존 3곳과도, 서로도 다르게 했다.

- 기존 3곳의 난수열·출력은 그대로다 (seed 42 기준선과 비교: json·csv는 바이트 동일, xlsx는 작성 시각이 들어가는 `docProps/core.xml` 말고 동일)
- 회사마다 `random.Random("<id>:<용도>:<seed>")`만 쓰고, 인물은 기존 3곳이 쓴 인물을 뺀 뒤 uuid로 나눈 조각에서 뽑아 서로 겹치지 않는다
- 파일 종류는 회사마다 다르다 (15명 스타트업에 퇴직금추계표가 없는 식). 없는 종류는 정답지 `hrFiles`에 없을 뿐 평가는 그대로 돈다
- 정답지 형식은 같다. 더한 키: 최상위 `profile`·`rosterLayout`, 직원명부 오류의 `detail`, 인사 파일의 `cleanSheets`·`clean`, 시트의 `subtotalRows`·`hiddenColumns`
- CSV 인사 파일(사아랩스)은 시트가 없어 `sheets`의 이름을 파일 이름(확장자 제외)으로 두었다. 평가 스크립트가 이 이름을 `sheet`로 넘긴다

| 회사 (id) | 파일 | 형식 특징 | 급여·근태 규칙 |
|---|---|---|---|
| 사아랩스 (`saalabs`) | 직원명부.csv, 조직.csv, 급여_2026하반기.csv, 연차현황.csv | UTF-8(명부·조직은 BOM 없음, 급여·연차는 BOM), 이름이 첫 열, 날짜 `24-03-02`·`19940302`·`1994.03.02` 혼재, 6개월이 한 파일에 세로로, 금액이 달마다 `1234560` / `"1,234,560"` / `"1,234,560원"`, 귀속월 `2026-10`·`2026.11` | 포괄임금제(고정연장 20시간), 입사·퇴사 월 달력일 일할, 대표이사 고용보험 0, 식대·자가운전·육아수당 각 20만 비과세 |
| 자차정밀 (`jachaprec`) | 직원명부.xlsx, 조직.csv, 급여대장_2026-12.xlsx, 교대근무기록_2026-12.xlsx | 명부: 제목 2행 + 병합 묶음 헤더, 주민번호 열 숨김(마스킹 값), 사번 8자리 숫자, 날짜 셀 사이에 서식 없는 일련번호·문자. 급여: 총괄 + **부서마다 시트**, 1~3행 제목·결재란, 5~6행 2줄 헤더, A조·B조 **소계 행이 중간에**, 통상임금 열 숨김. 조직: 폐지 부서 행(사용여부 N) | 단협상 토 8시간 유급 → 통상시급 = 통상임금/**243**, 근속수당 DATEDIF, 국민연금은 **기준소득월액(신고값)에 상·하한**, 건강·고용은 그달 과세분. 근태: 주야 2교대(주 단위 교대), **자정 넘는 야간**, 22~06시 야간, 30분 미만 버림 |
| 카타마트 (`katamart`) | 직원명부.csv, 조직.csv, 급여대장.xlsx, 근무시간표_12월.xlsx | 명부 CP949, 이메일 열 없음, 재직 `Y/N`, 시급에 쉼표·전각 숫자. 급여: 시트 `11월`·`12월`, **점포 소계 행**, 같은 열이 정규직 행은 값·파트타임 행은 수식. 근무시간표: **점포마다 시트, 날짜가 열**(가로 배치), 2행 일자·3행 요일 | 파트타임 기본급 = 시급×시간, **주휴수당**(주 15시간 이상), 공휴일 1.5배, 마감조 야간, **월 60시간 미만 4대보험 제외**, 10원 반올림, 소득세 1인 고정 |
| 파하공단 (`pahagongdan`) | 직원명부.xlsx, 조직.csv, 급여대장_2026.xlsx, 연가관리.xlsx | 명부: 마스킹 주민등록번호, 직원번호 앞자리 0 문자(`050012`), 임용일에 `2015년 3월 2일`·`2015/03/02`. 조직 3단계. 급여: 시트 `2026.10`~, 직급순 정렬, **봉급표(2026)와 봉급표(2025)가 같은 파일에** | 봉급 = `INDEX/MATCH(봉급표, 직급, 호봉)`, 임용 월마다 정기승급, 4급 이상 관리업무수당(봉급 9%)·시간외 없음, 시간외 단가 = 봉급×55%/209×1.5(공무원 방식), TRUNC 10원. 연가: 공무원 연가일수표 |

사고 없는 비교군(오탐 측정용): 사아랩스 `연차현황.csv` 전체, 자차정밀 급여 부서 시트 5개, 카타마트 근무시간표 점포 시트 3개, 파하공단 `2026.12` 시트 (정답지 `cleanSheets`·`clean`).
사고 행이 아닌 행은 모두 회사 규칙대로 맞게 계산돼 있다.

### 추가 회사의 직원명부 오류

기존 코드(REQUIRED·INVALID_EMAIL·DUPLICATE_KEY·INVALID_ENUM·DATE_ORDER·DUPLICATE_SUSPECTED·POSITION_TYPO·DATE_FORMAT)를 회사 규모에 맞게 넣고, 아래를 더했다 (모두 `detectable: false`).
같은 사람 두 번(DUPLICATE_SUSPECTED)은 기존처럼 다른 사람의 이름을 바꾸지 않고 **행을 하나 더** 넣었다 (재입사·정규직 전환 때 예전 행을 닫지 않음). 그래서 명부 행 수 = 인원 + 1~2.

| 코드 | 내용 | 회사 |
|---|---|---|
| DEPT_RENAMED | 2026-09 이름이 바뀐 부서의 옛 이름 (조직.csv에는 사용여부 N) | 자차 |
| STATUS_CONFLICT | 재직 상태인데 퇴사일 있음 (퇴사 처리 누락) | 자차, 사아 |
| EMPNO_FORMAT | 사번 자릿수가 다름 (`SA12` ↔ `SA012`) | 사아 |
| NOT_EMPLOYEE | 사업소득자(프리랜서 3.3%)가 근로자 명부에 | 사아 |
| RRN_BIRTH_MISMATCH | 마스킹 주민번호 앞 6자리 ≠ 생년월일 | 파하 |
| STEP_OUT_OF_RANGE | 호봉 0 (봉급표 1~32) — 매핑 없는 열이라 `field`는 헤더 이름 `호봉` | 파하 |

오류가 아닌 값 (오탐 확인용): 회사 고유 직급(`대표`·`시니어`, `기장`·`기정`), 고용형태 `촉탁직`·`무기계약직`, 생산직 이메일 빈 칸, 재직 `Y/N`.

### 추가 사고 유형

기존 유형(HARDCODED_STALE·SUM_RANGE_MISSING·OUT_MISSING·DUPLICATE_ROW·WEEKLY_52H_EXCEEDED·TEXT_NUMBER·USED_ON_HOLIDAY·DATE_ORDER·LOOKUP_NA)도 회사 양식에 맞게 다시 썼다.
새 유형:

| 유형 | 내용 | 회사·파일 | needs |
|---|---|---|---|
| DIVISOR_INCONSISTENT | 한 부서 시트만 통상시급 `/209` 숫자 (다른 시트는 설정!243) | 자차 급여 | FORMULA_READ, RULE_EXTRACTION, MULTI_SHEET |
| SUBTOTAL_DOUBLE_COUNT | 합계 행이 SUM 범위로 조 소계 행까지 더해 2배 → 총괄 시트로 전파 | 자차 급여 | TOTAL_ROW_SKIP, RECOMPUTE_CHECK |
| WRONG_YEAR_SHEET | 작년 12월 시트를 복사해 사람만 바꿈 (제목 `2025년`, 기본급 작년 금액, DATE(2025,12,31)) | 자차 급여 | PERIOD_LABEL_CHECK, CROSS_FILE_MATCH, FORMULA_READ |
| STALE_CAP | 설정 시트의 국민연금 기준소득월액 상한이 작년 값 | 자차 급여 | RATE_TABLE_BY_PERIOD, LEGAL_RULE_COMPARE |
| HOURS_MISMATCH_CROSS_FILE | 근태 월집계에서 옮겨 적은 연장시간 숫자 순서가 바뀜 (seed 42: 17.5 → 71.5) | 자차 급여 | CROSS_FILE_MATCH |
| MIDNIGHT_NOT_HANDLED | 주간 양식을 복사해 붙인 행의 근무(분) 수식에 MOD가 없어 야간 근무가 음수 | 자차 근태 | FORMULA_READ, RECOMPUTE_CHECK, RULE_EXTRACTION |
| HOLIDAY_WORK_AS_WEEKDAY | 성탄절 특근을 근무구분 '주간'으로 기록 | 자차 근태 | HOLIDAY_CALENDAR, CROSS_FIELD_RULE |
| RESIGNED_STILL_PAID | 퇴사자에게 다음 달 급여 전액 | 사아 급여 | CROSS_FILE_MATCH |
| MONTH_ROW_MISSING | 한 달 행이 없고 다음 달에 소급 합산 | 사아 급여 | PERIOD_CONTINUITY, CROSS_FILE_MATCH |
| TOTAL_MISMATCH | 기본급만 고치고 지급합계·실지급은 그대로 (수식 없는 CSV) | 사아 급여 | RECOMPUTE_CHECK |
| NEGATIVE_NET_PAY | 무급휴직 달에 보험료·대출상환만 공제해 실지급 음수 | 사아 급여 | RANGE_CHECK, RECOMPUTE_CHECK |
| PRORATION_MISSING | 월 중간 입사자에게 한 달 치 전액 | 사아 급여 | CROSS_FILE_MATCH, RECOMPUTE_CHECK |
| NP_CAP_NOT_APPLIED | 대표 국민연금을 상한 없이 계산 | 사아 급여 | LEGAL_RULE_COMPARE, RECOMPUTE_CHECK |
| NONTAX_OVER_LIMIT | 자가운전보조금 30만원 전액 비과세 (한도 20만) | 사아 급여 | LEGAL_RULE_COMPARE, RECOMPUTE_CHECK |
| MIN_WAGE_VIOLATION | 포괄임금 환산 시급 (기본급+식대)/209 < 최저임금 | 사아 급여 | LEGAL_RULE_COMPARE, RULE_EXTRACTION |
| MIN_WAGE_STALE | 파트타이머 시급이 2025년 최저임금 그대로 | 카타 급여 | LEGAL_RULE_COMPARE, RATE_TABLE_BY_PERIOD |
| WEEKLY_HOLIDAY_PAY_MISSING | 주 15시간 이상인데 주휴시간 0 입력 | 카타 급여 | LEGAL_RULE_COMPARE, CROSS_FILE_MATCH |
| INSURANCE_EXEMPTION_WRONG | 월 60시간 이상인데 4대보험 칸에 0 입력 (단시간 제외 오적용) | 카타 급여 | HARDCODED_IN_FORMULA_COLUMN, FORMULA_READ, LEGAL_RULE_COMPARE |
| HOLIDAY_PREMIUM_MISSING | 한 점포가 공휴일 근무를 일반 근무시간에 넣어 가산 누락 | 카타 급여 | HOLIDAY_CALENDAR, CROSS_FILE_MATCH, LEGAL_RULE_COMPARE |
| ROW_OUTSIDE_SUBTOTAL | 신규자 행을 점포 소계 아래에 추가 → 소계·총계에서 빠짐 | 카타 급여 | TOTAL_ROW_SKIP, RECOMPUTE_CHECK |
| CROSS_SHEET_DUPLICATE | 타점 지원 근무를 두 점포 시트에 모두 기록 | 카타 근무시간표 | DUPLICATE_DETECT, MULTI_SHEET |
| STALE_PAY_TABLE | 한 달 시트의 봉급 수식이 같은 파일의 작년 봉급표 참조 | 파하 급여 | FORMULA_READ, RATE_TABLE_BY_PERIOD, MULTI_SHEET |
| STEP_NOT_ADVANCED | 임용 월이 돌아왔는데 호봉 미승급 (명부는 승급) | 파하 급여 | CROSS_FILE_MATCH, PERIODIC_STEP_RULE |
| ALLOWANCE_RULE_CONFLICT | 관리업무수당 받는 4급에 시간외수당을 값으로 입력 | 파하 급여 | CROSS_FIELD_RULE, HARDCODED_IN_FORMULA_COLUMN, RULE_EXTRACTION |
| LEAVE_OVERUSED | 병가를 종류 '연가'로 기록 → 사용 > 부여, 잔여 음수 | 파하 연가 | CROSS_FIELD_RULE, RECOMPUTE_CHECK |

법정 기준과 다른 회사 규칙(사고 아님, `legalDeviations`): 파하공단 시간외 단가(봉급의 55% 기준)와 공무원 연가일수표(1~4년차는 법보다 적음),
그 밖에 STALE_CAP·NP_CAP_NOT_APPLIED·NONTAX_OVER_LIMIT·MIN_WAGE_*도 함께 기록.

새 needs:

| needs | 뜻 |
|---|---|
| PERIOD_LABEL_CHECK | 시트 제목·지급일에 적힌 기간과 파일·시트의 기간 대조 |
| HIDDEN_CELLS | 숨긴 열·행의 값도 읽고 검사 |
| PERIOD_CONTINUITY | 월별 이력에서 빠진 달·소급 합산 찾기 |
| RANGE_CHECK | 음수 실지급처럼 있을 수 없는 범위의 값 |
| PERIODIC_STEP_RULE | 호봉 정기승급 같은 날짜 기반 규칙 |

가정값: 국민연금 기준소득월액 하한 40만·상한 637만(작년 상한 617만)을 2026년 전체에 적용, 2025 최저임금 10,030원, 봉급표·직급보조비는 공무원 표를 흉내 낸 값(실제 표 아님).
수식 검산(seed 42, `check_formulas.py`): 자차 급여 10,170셀, 카타 급여 3,801셀·근무시간표 92셀, 파하 급여 10,256셀·연가 972셀 불일치 0. 자차 교대근무기록 53,364셀도 불일치 0 (이 파일은 검사에 8분 넘게, 메모리 7GB 가까이 든다).

## 평가에서 드러난 이관 서비스 약점과 보완 상태

브랜치 `feat/migration-excel-reading`에서 보완. 평가는 seed 42 기준이고, 생성기와 검증 규칙을 같은 쪽이 만들었으므로 회귀 확인일 뿐이다.

| 약점 (보완 전 근거) | 보완 |
|---|---|
| 첫 번째 시트만 읽음 (가나 급여대장은 `설정` 9행만) | 모든 시트 읽기 + `sheet` 지정, 없으면 entity 필드와 가장 잘 맞는 시트 → 인사 엑셀 58개 시트 행 수·헤더 모두 정답과 일치 |
| '값 2칸 이상인 첫 행 = 헤더' (다라 급여 2행, 4대보험 2행이 헤더로) | 글자 칸 기준 헤더 찾기, 병합 셀 처리 |
| 합계 행·합계 아래 행을 데이터로 읽음 | 합계 행 제외 + 경고, 합계 아래 행 경고 |
| `#N/A`·`#REF!`가 빈 값 | 오류값 보존 + 차단 |
| 수식 문자열을 안 읽음 | 수식 저장, 열별 규칙 추출 API, 값 직접 입력·빈 칸·다른 수식·다른 시트와 다른 수식 경고 |
| 받을 entity 없음 (근태·발령·퇴직금) | `REFERENCE`(분석 전용) entity |
| 파일 사이 대조 없음 | 직원명부 사번·이름 대조 (앞자리 0 보정) |
| 직위 오타 0/6, 마바 조직 매핑 1/3 | 자모 기준 코드값 오타 경고 6/6, 영문 조직 컬럼 동의어 3/3 |

| 평가 | 보완 전 | 보완 후 |
|---|---|---|
| 직원명부 오류 검출 | 657/663 | 663/663, 오탐 0 |
| 인사 엑셀 사고 검출 | 0/123 | 31/123, 정답지에 없는 경고 0 |
| 직원명부 대조 경고 (직원명부에 넣은 오류의 결과) | - | 507건 모두 직원명부 오류로 설명됨 |

아직 못 잡는 사고(92건)는 대부분 업무 규칙이 필요하다: 법정 기준 비교(연차 가산·52시간·나이 요건), 같은 행 필드 관계(발령일 < 입사일, 상실일),
파일 간 값 대조(급여↔근태 시간, 연차일 출근, 신청 없는 연장), 휴일 달력, 중복 행. 급여·연차 entity 필드 정의가 정해져야 붙일 수 있는 것이 많다.

## 주의

생성기의 오류 유형과 이관 서비스의 검증 규칙을 같은 사람이 만들었기 때문에, 검출률 100%는 **규칙이 의도대로 동작한다는 회귀 확인**이지 독립적인 정확도 측정이 아니다.
정확도를 주장하려면 생성 규칙을 모르는 사람이 만든 데이터나 실제 회사 양식 샘플로 따로 재야 한다.
