# 근태 서비스 남은 작업

> 마지막 업데이트: 2026-11-03

## 다음에 할 일

1. 결재 취소 연동 방식(D3) 확인 후 신청서 회수 `POST /me/requests/{id}/cancel`
2. `/duties` API 확정되면 관리자 근태 현황 (티켓 2) 재개
3. 휴가·user-service 이벤트 발행이 나오면 수신 추가

## 1. 진행 중인 티켓

### [근태] 관리자 근태 현황 (티켓 2, 보류)
브랜치 `feat/attendance-admin-status` (push 완료, MR 아직 안 만듦)

- [x] user-service 사원 목록 클라이언트 (`UserClient`, 토큰 전달)
- [ ] 조회 범위 판단 ← **직책 이름 목록·`/duties` API 확정 대기**
- [ ] 일자별 현황 `GET /admin/daily` (이상 집계 + 사원별 목록)
- [ ] 기간별 통계 `GET /admin/period` (최대 92일)
- [ ] 엑셀 다운로드 `GET /admin/export` (선택)
- [ ] `/summary` 권한 제한: 지금은 누구나 모든 사원을 볼 수 있음 → 상급자만 하위 사원 조회

**확정된 내용**
- 응답: 일자별은 상단 집계(대상·출근·미출근·휴일·근태이상 유형별) + 사원별 목록, 기간별은 사원별 합계 + 전체 합계
- `empStatus`를 생략하면 재직자만. 명세의 `ON_LEAVED`는 user-service 값인 `ON_LEAVE`로 고쳐야 함
- 미래 날짜는 400 (전원이 미출근으로 잡히므로)

**권한 (팀 결정)**
- 토큰의 role(USER/ADMIN)은 쓰지 않는다
- 팀장급 이상 직책이 자기 부서원 전부(본인·동급 포함)를 본다. 다른 부서 요청은 403
- 팀장급 판단은 user-service `GET /api/v1/duties/{dutyId}`의 직책 이름으로 (설정 파일에 ID를 두지 않음)
- 전사 조회는 대표급 직책. 인사팀 전사 조회는 부서명 조회 API가 생기면 추가
- 판단은 일자별·기간별·엑셀·`/summary`가 공유하는 한 곳에 둔다

> user-service에 부서명 조회 API가 없어 부서는 ID로만 응답

### [근태] 성능
- [ ] `employee_work_group (emp_id)` 인덱스 추가 (ERD 확인 필요)
- [ ] `work_status_log (emp_id, work_date)` 인덱스 추가 (ERD 확인 필요)

## 2. 정책만 정하면 바로 할 수 있는 것

- [ ] **휴게 상태 추가** : "휴게"를 별도 상태로 두면 휴게시간 부족(`SHORT_BREAK`) 판정 가능
- [ ] **연도별 공휴일 등록 방식** : 마이그레이션 / 관리자 API / 공공데이터 API 중 결정 (현재 2027년까지 등록)

## 3. 다른 서비스·팀 결정이 필요한 것

| 작업 | 필요한 것 |
|---|---|
| 신청서 회수 `POST /me/requests/{id}/cancel` | 결재 취소 연동 방식 (결정 문서 D3) |
| 휴가 승인·취소 수신, 휴가 중 출근(`WORK_ON_LEAVE`) 판정, 반차 반영 | 휴가 서비스 이벤트 발행 (수신 기반은 완료) |
| 입사·퇴사 수신 (기본 근무그룹 배정) | user-service 이벤트 발행 (수신 기반은 완료) |
| 급여 집계에 유급휴가 반영 | 휴가 서비스 연동 |
| 관리자 근태 조회 범위 판단 (티켓 2) | 직책 이름 목록, `/duties` API 형태 확정 |
| 기존 관리자 API 권한 (직원 타임라인, 로그 등록·수정·삭제) | 누가 쓸지 결정 (지금은 role=ADMIN이라 실사용자는 못 씀) |
| 공휴일 조회 API 연동 | 휴가 서비스의 호출 경로(`/api/v1/attn/holidays`)·토큰 전달 수정 |
| 직위(position) 정리 | user-service에 position 테이블이 아직 남아 있음 (직급으로 통일하기로 함) |

> 코드 내 관련 위치는 `TODO` 주석으로 표시되어 있음 (IntelliJ TODO 창에서 확인)

## 4. 팀 회의 안건

1. 팀장급 이상 직책 이름 목록, `/duties` API 형태 (user-service)
2. 부서명 조회 API, 인사팀 전사 조회 여부 (user-service)
3. 기존 관리자 API(타임라인, 로그 수정)를 누가 쓸지
4. 결재 취소 연동 방식 (결정 문서 D3)
5. user-service `employee.work_group_id`와 근태 `employee_work_group` 중복 정리
6. 연도별 공휴일 등록 방식
7. user-service position 테이블 정리 시점

## 5. 참고

- **로컬 결재 연동 테스트** : 결재 서비스의 `Backend/approval-service/compose.yaml`로 띄움 (Kafka `localhost:19092`, 결재 8083). 근태는 기본값 `localhost:19092`로 붙음
- **Kafka 수신 실패 처리** : 1초 → 2초 → 4초 재시도 후 `approval.document.result.DLT`로 이동
- **배포 장애 (2026-11-03)** : Kafka 리스너 추가 후 개발 서버에서 근태 컨테이너 unhealthy. 앱이 켜질 때 Kafka 주소를 찾을 수 있어야 함 (서버의 kafka 컨테이너·네트워크 확인)

## 최근 완료

- 결재 승인 반영 (티켓 3) : 초과근무 승인 시간 분리, 휴일대체 반영, 근무시간 수정 적용, `applyApprovalResult`
- 결재 결과 수신 (티켓 4) : `approval.document.result` 리스너, 재시도 후 DLT
- 신청서 상신 시 결재 문서 생성 (결재선은 결재 서비스가 자동 생성)
- 공휴일 조회 API, 2027년 공휴일, 이력 조회 최대 31일
