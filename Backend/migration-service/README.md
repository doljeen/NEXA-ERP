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
| POST | `/{id}/files?entity=` | 업로드 → 읽기·매핑 추천·검증 |
| GET | `/{id}/files/{fileId}` | 파일 진단, 매핑, 오류 건수 |
| PUT | `/{id}/files/{fileId}/mappings` | 매핑 확정 → 재검증 |
| PATCH | `/{id}/files/{fileId}/rows/{rowNo}` | 행 직접 수정 → 재검증 |
| GET | `/{id}/issues` | 오류: 심각도별 건수, 원인별 묶음, 행 단위 목록 |
| POST | `/{id}/fixes` | 자동 정리 일괄 적용 |
| POST | `/{id}/loads?dryRun=` | 적재 / 미리보기 (차단·자동정리 0건일 때만) |
| DELETE | `/{id}/batches/{batchId}` | 묶음 되돌리기 |
| GET | `/{id}/reconciliation` | 대사 |

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
- 코드 매핑(직위·부서 값 대조), 참조 검증, 엑셀 양식 다운로드, 전환 완료
