# Leave Service

## 관리자 휴가 유형 조회

`GET /api/v1/leaves/admin/types`

- 등록된 모든 유형을 `leave_type_id` 오름차순으로 반환한다.
- `user-service`와 동일한 `ApiResult<T>` 응답을 사용한다. 데이터가 없으면 HTTP 200과 `data: []`를 반환한다.
- 잔액 유형이 없어도 결과에 포함하며 `balanceType`은 `null`이다.
- 비활성 잔액 유형도 포함한다. `maxMinutes`는 DB 값에 따라 `null`일 수 있다.
- 휴가 유형의 활성 여부는 `isActive`로 반환하며 비활성 휴가 유형도 조회한다.
- 읽기 전용 트랜잭션 안에서 LEFT JOIN FETCH 한 번으로 조회하고 DTO로 변환한다.

```json
{
  "success": true,
  "error": null,
  "data": [
    {
      "leaveTypeId": 1,
      "name": "연차",
      "paid": true,
      "balanceMode": "ACCOUNT",
      "requestUnit": "DAY",
      "maxMinutes": null,
      "balanceType": {"balanceTypeId": 1, "name": "연차", "active": true},
      "isActive": true
    }
  ]
}
```

## 관리자 휴가 유형 등록

`POST /api/v1/leaves/admin/types`

요청 예시 (`balanceTypeId`는 실제 등록된 잔액 유형 ID):

```json
{
  "balanceTypeId": 1,
  "name": "연차",
  "isPaid": true,
  "balanceMode": "ACCOUNT",
  "requestUnit": "DAY",
  "maxMinutes": 480,
  "isActive": true
}
```

HTTP 201 응답 예시 (생성 ID는 DB에 따라 달라진다):

```json
{
  "success": true,
  "error": null,
  "data": {
    "leaveTypeId": 1,
    "name": "연차",
    "paid": true,
    "balanceMode": "ACCOUNT",
    "requestUnit": "DAY",
    "maxMinutes": 480,
    "balanceType": {"balanceTypeId": 1, "name": "연차", "active": true},
    "isActive": true
  }
}
```

- `name`은 공백 불가, 최대 100자이며 `isPaid`, `balanceMode`, `requestUnit`은 필수다.
- `ACCOUNT`는 `balanceTypeId`가 필수다. `NONE`은 잔액 유형 없이 등록할 수 있다.
- `balanceTypeId`를 입력하면 모드와 관계없이 존재 여부를 검증한다.
- 존재하지 않는 잔액 유형이면 HTTP 404와 `LEAVE_001`을 반환한다.
- `maxMinutes`는 생략/null이 가능하며 입력된 값은 양수여야 한다.
- `isActive`는 생략/null이면 true, 명시적인 false는 그대로 저장한다.
- 요청의 `isPaid`는 기존 GET 계약을 유지해 응답에서는 `paid`로 반환한다.
- Bean Validation 및 ACCOUNT 잔액 유형 누락 오류는 HTTP 400과 `COMMON_001`을 반환한다.
- 권한 검증과 등록은 Service의 쓰기 트랜잭션에서 수행한다.

스키마 변경은 `V2__add_leave_type_is_active.sql`로 적용한다.
V1은 변경하지 않으며 기존 휴가 유형은 활성 상태를 유지한다.

## 공통 예외 처리

`BusinessException`과 `ErrorCode`를 `GlobalExceptionHandler`에서 처리한다.
공통 오류 코드와 메시지는 `user-service` 규격을 따른다.
인증 미연동 상태에는 leave-service 전용 `LEAVE_AUTH_001` 코드와 HTTP 503을 사용한다.
현재 사용하지 않는 JWT 및 Spring Security 예외 처리는 포함하지 않는다.
검증/JSON 오류는 400, DB 제약 위반은 409, 없는 리소스는 404,
예상하지 못한 오류는 내부 정보를 노출하지 않고 500으로 응답한다.

```json
{
  "success": false,
  "error": {"code": "AUTH_009", "message": "접근 권한이 없습니다."},
  "data": null
}
```

## 개발용 인증

실제 인증 및 관리자 권한 검증은 아직 구현하지 않았다.
로컬 개발 시 `--spring.profiles.active=dev` 또는 `SPRING_PROFILES_ACTIVE=dev`를 지정한다.
기존 DB/Eureka 환경 변수 설정도 필요하다.
`dev`에서만 임시 직원 ID `1`을 반환하고 관리자 검증을 항상 통과한다.
`prod` 또는 `production`이 함께 활성화되면 우회가 비활성화된다.
그 외 프로필과 기본 실행에서는 실제 인증 미구현 상태로 `503`을 반환한다.
운영 배포에서는 반드시 운영 프로필을 명시하고 `dev`를 사용하지 않는다.

HR 연동 시 `EmployeeAccess` 구현을 실제 인증 사용자→직원 emp_id 조회,
emp_id→직책 기반 관리자 검증으로 교체한다. Service의 조회 로직은 그대로 유지한다.
관리자 검증 결과가 false이면 `403`을 반환한다.

## 테스트

외부 DB 없이 신규 기능 테스트 실행:

```powershell
.\gradlew.bat test --tests '*LeaveTypeServiceTest' --tests '*AdminLeaveTypeControllerTest' --tests '*EmployeeAccessTest' --tests '*GlobalExceptionHandlerTest'
```

기존 `LeaveServiceApplicationTests`는 전체 애플리케이션 및 실제 DB 연결 환경이 필요하다.
V1 SQL은 수정하지 않으며 Entity는 해당 스키마의 두 테이블만 매핑한다.

Docker가 실행 중이면 PostgreSQL 17 Testcontainers 통합 테스트까지 실행할 수 있다:

```powershell
.\gradlew.bat test --tests '*Test'
```

위 명령은 기존 외부 환경 의존 `LeaveServiceApplicationTests`를 제외한다.
통합 테스트는 기존 DB 대신 별도 컨테이너를 생성하며, V1→V2 업그레이드와 기존 행의 기본값,
POST 검증/저장/권한 거부, 등록 후 GET 조회를 검증한다.
통합 테스트의 권한 거부 분기는 `EmployeeAccess` mock으로 확인하고,
실제 프로필별 임시 인증 활성화 제한은 기존 `EmployeeAccessTest`에서 검증한다.
