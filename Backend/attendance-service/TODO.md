# 근태 서비스 남은 작업

> 마지막 업데이트: 2026-10-28

## 1. 혼자 바로 할 수 있는 것

### [근태] 성능 최적화
- [ ] `employee_work_group (emp_id)` 인덱스 추가
- [ ] `work_status_log (emp_id, work_date)` 인덱스 추가
- [ ] 변경 이력 조회(`GET /me/history`) 기간 제한 또는 페이징

### [근태] 배포 환경 구성
- [ ] Actuator 의존성, health 노출 설정 (user-service 기준)
- [ ] `Dockerfile`, `.dockerignore`
- [ ] `.gitlab/ci/attendance.yml`, 루트 `.gitlab-ci.yml` include
- [ ] 루트 `compose.dev.yml`에 attendance-service 등록

> 루트 파일은 팀 공용이라 MR에 따로 언급

## 2. 정책만 정하면 바로 할 수 있는 것

- [ ] **휴게 상태 추가** : "휴게"를 별도 상태로 두면 휴게시간 부족(`SHORT_BREAK`) 판정 가능
- [ ] **연도별 공휴일 등록 방식** : 마이그레이션 / 관리자 API / 공공데이터 API 중 결정 (현재 2026년만 등록)

## 3. 다른 서비스·팀 결정이 필요한 것

| 작업 | 필요한 것 |
|---|---|
| 신청서 상신·회수, 결재 결과 반영 (근무시간 수정 적용, 초과근무 승인, 휴일대체 적용) | 결재 서비스 연동 방식 |
| 관리자 일자별 현황, 기간별 통계, 엑셀 다운로드 | 직원 정보(부서, 재직 상태) 연동 방식 |
| 휴가 승인·취소, 입사·퇴사 이벤트 수신, 휴가 중 출근(`WORK_ON_LEAVE`) 판정, 반차 반영 | 메시지 브로커, 휴가 서비스 |
| 급여 집계에 유급휴가 반영 | 휴가 서비스 연동 |
| JWT 인증, 관리자 권한 확인, 부서장 권한 범위 | 팀 인증 방식, 권한 정의 |

> 코드 내 관련 위치는 `TODO` 주석으로 표시되어 있음 (IntelliJ TODO 창에서 확인)

## 4. 팀 회의 안건

1. 메시지 브로커 선택 (Kafka, RabbitMQ 등)
2. 직원 정보를 근태에 사본으로 둘지, 매번 user-service를 호출할지
3. 결재 서비스 상신 요청 API와 결과 이벤트 형식
4. 인증 방식 통일 (user-service RS256 쿠키 ↔ Gateway HS256 Bearer 불일치)
5. user-service `employee.work_group_id`와 근태 `employee_work_group` 중복 정리
6. 부서장 권한 정의

## 추천 순서

1. 성능 최적화 → 배포 환경 구성
2. 회의 안건 결정 후 결재 연동(신청서 상신)부터 진행
