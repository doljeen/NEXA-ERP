# migration-service (이관)

기존 시스템·엑셀의 인사 데이터를 올려서 검증·정리한 뒤 각 서비스로 적재하는 서비스.

> 포트(8087)와 DB 이름(`nexa_migration`)은 팀 합의 전 가정값이다.

## 흐름

```text
작업 생성 → 파일 업로드(읽기·매핑 추천·검증 자동) → 매핑 수정 / 자동 정리 / 행 직접 수정
→ 미리보기(dryRun) → 적재(조직 → 직원) → 대사 → 필요하면 묶음 단위 되돌리기
```

## 단독 실행

```bash
# 1) 이관용 Postgres (팀 compose의 postgres를 써도 됨)
docker run -d --name migration-pg-local -e POSTGRES_DB=nexa_migration \
  -e POSTGRES_USER=nexa_migration_app -e POSTGRES_PASSWORD=localpw -p 127.0.0.1:5434:5432 postgres:17-alpine

# 2) 실행 (Eureka·Config 없이)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/nexa_migration \
MIGRATION_DB_PASSWORD=localpw \
./gradlew bootRun --args='--spring.profiles.active=local'
```

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `SERVER_PORT` | 8087 | |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/nexa_migration` | |
| `MIGRATION_DB_PASSWORD` | (없음) | |
| `HR_SERVICE_URL` | `http://localhost:8081` | user-service 이관용 내부 API 주소 |

## 테스트

```bash
./gradlew test   # Docker 필요 (Testcontainers로 Postgres 17을 띄움)
```

user-service의 이관용 내부 API는 아직 없어서, 적재 테스트는 `FakeHrServer`(실제 HTTP 가짜 서버)로 확인한다.

## API (`/api/v1/admin/migrations`)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/` | 작업 생성 |
| GET | `/{id}` | 작업 상세 (파일, 적재 묶음) |
| POST | `/{id}/files?entity=&sheet=` | 업로드 → 읽기·매핑 추천·검증. `sheet` 없으면 entity 필드와 가장 잘 맞는 시트 |
| GET | `/{id}/files/{fileId}` | 파일 진단, 매핑, 오류 건수 |
| GET | `/{id}/files/{fileId}/formulas` | 엑셀 수식에서 뽑은 열별 규칙, 규칙에서 벗어난 행, 같은 양식 다른 시트와 비교 |
| PUT | `/{id}/files/{fileId}/mappings` | 매핑 확정 → 재검증 |
| PATCH | `/{id}/files/{fileId}/rows/{rowNo}` | 행 직접 수정 → 재검증 |
| GET | `/{id}/issues` | 오류: 심각도별 건수, 원인별 묶음, 행 단위 목록 |
| POST | `/{id}/fixes` | 자동 정리 일괄 적용 |
| POST | `/{id}/loads?dryRun=` | 적재 / 미리보기 (차단·자동정리 0건일 때만) |
| DELETE | `/{id}/batches/{batchId}` | 묶음 되돌리기 |
| GET | `/{id}/reconciliation` | 대사 |

## 엑셀 읽기·검사

- 모든 시트를 읽는다. 응답의 `sheets`에 시트별 행 수·헤더 행·수식 셀 수, `sheetName`에 읽은 시트
- 헤더 행: 위쪽 30행에서 글자 칸이 가장 많은 행의 절반 이상인 첫 행 (제목·귀속월·요율 행 건너뜀). 세로 병합은 채우고, 가로 병합 묶음 머리글은 이름이 겹치는 열에만 붙임 (`07월 연장`)
- 합계·소계 행은 데이터에서 빼고 `TOTAL_ROW_SKIPPED`, 합계 아래 데이터 행은 `ROW_AFTER_TOTAL`
- 수식 셀은 저장된 계산값을 값으로 쓰고, 수식 문자열·오류값은 `staging_row.meta`에 따로 저장. 오류값 셀은 `FORMULA_ERROR`(차단)
- 수식 규칙: 열마다 대표 수식(`=ROUNDDOWN(({기본급}+{직책수당})/설정!$B$9,0)`), 값 직접 입력 `HARDCODED_VALUE`, 빈 칸 `BLANK_IN_FORMULA_COLUMN`,
  다른 모양 `FORMULA_INCONSISTENT`, 같은 파일·같은 헤더의 다른 시트와 대표 수식이 다름 `FORMULA_DIFFERS_ACROSS_SHEETS` (모두 경고)
- 검산 (모두 경고, 계산값은 `suggestion`에):
  - 대표 수식이 같은 행 칸의 덧셈·뺄셈·SUM(`=SUM({기본급}:{식대},{연장수당}:{휴일수당})`, `={발생일수}-{사용일수}`)이면 행마다 다시 계산해 저장값과 비교 `RECALC_MISMATCH`.
    차이가 항목 한 열의 값과 같으면 그 열을 알려 줌 (SUM 범위 누락·숫자로 바꿔 넣은 합계)
  - 수식이 없는 파일(CSV·값만 붙여 넣은 엑셀): 열 이름으로 지급계·공제계·실지급을 찾아 `지급계 = 왼쪽 지급 항목 합`, `공제계 = 지급계와 공제계 사이 공제 항목 합`,
    `실지급 = 지급계 − 공제계`로 추정해 검산. 대부분 행이 안 맞고 차이가 한 항목으로 설명되지 않으면 추정이 틀린 것으로 보고 한 건으로 묶음
  - 지급계·공제계 수식 범위에 들어가지 않은 지급·공제 항목 열 `TOTAL_ITEM_MISSING` (시트 단위)
  - ROUND·VLOOKUP 등이 섞인 수식(통상시급, 4대보험, 소득세)은 아직 다시 계산하지 않음
- 급여 법정 기준 비교 (모두 경고, 수식이 아니라 값으로 보므로 설정 시트 참조·CSV에도 동작). 귀속연도는 시트 이름 → 파일 이름(`2026-07`)에서 찾고, 기준표는 `StatutoryRates`(2023~2026)
  - 4대보험: 열마다 (기준 금액, 요율) 조합 중 가장 많은 행을 설명하는 것을 찾아(60% 이상) 귀속연도 요율과 다르면 `INSURANCE_RATE_OUTDATED` (시트 단위).
    기준 금액이 비과세 식대를 포함한 지급계이면 `INSURANCE_BASE_INCLUDES_MEAL`
  - 가산수당: 통상시급 × 시간 × 법정 배율(연장 1.5, 야간 0.5, 휴일 8시간까지 1.5·초과 2.0)보다 적은 행 `PREMIUM_BELOW_STATUTORY`.
    휴일 8시간 초과분을 대부분 1.5배로 계산했으면 시트 단위 한 건. 상시 5인 미만 사업장은 적용 제외라 경고로만 둠
- 직원명부 대조: 직원명부가 아닌 파일의 사번이 같은 작업의 직원명부에 있는지 `EMP_NOT_IN_ROSTER`, 이름이 같은지 `EMP_NAME_MISMATCH`,
  사번 빈 칸 `EMP_KEY_MISSING`(이름으로 찾은 사번 제안). 숫자 셀이라 앞자리 0이 빠진 사번은 명부 자리수로 채워 대조하고 `EMPNO_ZERO_PADDED`
- 코드값 오타: 직위·직급 등에서 드문 값이 흔한 값과 한글 자모 하나만 다르면 `CODE_TYPO_SUSPECTED` + 제안값
- `REFERENCE` entity: 적재하지 않는 참고 자료(근태기록·발령대장 등)를 올려 위 검사·규칙 추출만 한다

## 대상 서비스 내부 API (user-service가 구현해야 함, 합의 필요)

```text
POST   /internal/v1/hr/migration/{orgs|employees}/upsert?batchId=   {"rows":[{"rowNo":2,"data":{...}}]}
       → ApiResult{data:{created, updated}}
DELETE /internal/v1/hr/migration/batches/{batchId}
GET    /internal/v1/hr/migration/batches/{batchId}/summary           → ApiResult{data:{count}}
```

## 아직 안 된 것

- 인증 연동 (지금은 API가 열려 있고 `companyId`를 요청 본문으로 받음)
- 임시 테이블(`staging_row`) 민감정보 암호화
- 업로드·적재 비동기 처리 (지금은 동기)
- 대상 서비스 코드 목록과 대조하는 코드 매핑, 엑셀 양식 다운로드, 전환 완료
- 급여·연차 등(`PAY_*`, `LEAVE_BALANCE`) 필드 정의: 대상 서비스와 합의 전이라 없음 (지금은 읽기·수식 검사·직원명부 대조만)
- 수식 규칙의 설정 시트 참조(`설정!$B$9`)를 값·라벨로 풀어 보여 주기, ROUND·VLOOKUP이 섞인 수식 검산, 국민연금 상·하한, 파일 사이 대조(4대보험 보수월액 ↔ 급여)
