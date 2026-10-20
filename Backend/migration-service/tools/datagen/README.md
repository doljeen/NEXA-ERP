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

# 2) 가상 회사 생성 → out/<회사>/조직.csv, 직원명부.xlsx|csv, answer_key.json
python -I generate.py ~/personas/personas.parquet out --seed 42

# 3) 이관 서비스(localhost:8087)에 올리고 정답지와 비교 → out/evaluation.md
python -I evaluate.py out
```

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

## 주의

생성기의 오류 유형과 이관 서비스의 검증 규칙을 같은 사람이 만들었기 때문에, 검출률 100%는 **규칙이 의도대로 동작한다는 회귀 확인**이지 독립적인 정확도 측정이 아니다.
정확도를 주장하려면 생성 규칙을 모르는 사람이 만든 데이터나 실제 회사 양식 샘플로 따로 재야 한다.
