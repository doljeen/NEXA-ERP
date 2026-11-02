# 결재 문서 생성 요청 가이드 (approval-service)

결재 문서를 만들 때 어떤 데이터를 보내야 하는지 정리한 문서입니다.
결재 화면(프런트엔드)과 결재가 필요한 서비스(leave-service, attendance-service)가 대상입니다.

## 요청

```
POST /api/v1/documents
Authorization: Bearer {access token}
Content-Type: application/json
```

- **기안자는 토큰의 사원**입니다. 요청 본문에 기안자를 넣지 않습니다.
- 다른 서비스가 대신 호출할 때도 **신청한 사원의 토큰**으로 호출해야 그 사원이 기안자가 됩니다.

## 요청 본문

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `title` | string | O | 제목 (최대 255자) |
| `formType` | string | O | 문서 양식. 아래 [양식별로 보내야 하는 값](#양식별로-보내야-하는-값) 참고 |
| `approvalLines` | array | O | 결재선. 최소 1명 |
| `sourceType` | string | 조건부 | 문서가 만들어진 곳: `DIRECT` \| `LEAVE` \| `ATTENDANCE`. 생략하면 `DIRECT` |
| `sourceId` | number | 조건부 | 원본 신청 ID. `DIRECT`가 아니면 필수, `DIRECT`면 보내지 않음 |
| `requestKey` | string (UUID) | 조건부 | 생성 요청 키. **요청하는 쪽이 만들어서** 보냄. `DIRECT`가 아니면 필수, `DIRECT`면 선택. 아래 [requestKey 만드는 법](#requestkey-만드는-법) 참고 |

> 문서번호(`docNo`)는 서버가 만듭니다. 요청에 넣어도 무시합니다.

### requestKey 만드는 법

`requestKey`는 approval이 "이 요청이 아까 그 요청의 재시도인지" 알아보는 값입니다.
두 요청이 같은 신청에서 나왔다는 건 **요청을 보낸 쪽만 알기 때문에** approval이 대신 만들 수 없습니다.
(응답으로 받는 방식도 안 됩니다. 응답을 못 받은 상황, 즉 타임아웃에서 쓰는 값이기 때문입니다.)

규칙

1. **신청 하나당 키 하나.** 요청을 시도할 때마다 새로 만들면 안 됩니다. 재시도할 때마다 새 UUID를 쓰면 중복을 막지 못합니다.
2. **결재 요청을 보내기 전에 DB에 저장.** 요청 도중 서비스가 재시작돼도 같은 키로 재시도하거나 조회할 수 있어야 합니다.
3. **재시도·조회에 같은 키 사용.** 결과를 모르면 같은 키로 다시 생성 요청하거나 [조회](#요청-키로-문서-조회)합니다.

```java
// 신청을 저장할 때 키를 함께 만든다 (결재 요청 전)
leaveRequest.approvalRequestId = UUID.randomUUID();
leaveRequestRepository.save(leaveRequest);

// 결재 요청. 재시도해도 같은 값을 쓴다
approvalClient.create(new CreateDocument(..., leaveRequest.getApprovalRequestId()));
```

| 서비스 | 할 일 |
|---|---|
| leave-service | 이미 신청마다 `approvalRequestId`(UUID)를 만들어 저장함. 그 값을 `requestKey`로 보내면 됨 |
| attendance-service | 근태 신청에 UUID 컬럼을 추가해 신청 저장 시 만들고, 결재 요청에 담아 보냄 |
| 프런트 (직접 작성) | 선택. 작성 화면을 열 때 하나 만들어 두면 상신 버튼을 두 번 눌러도 문서가 하나만 생김 |

### 재시도와 requestKey

같은 `requestKey`로 다시 요청하면 문서를 새로 만들지 않습니다. 타임아웃처럼 결과를 모를 때 **같은 키로 다시 보내도 안전합니다.**

| 상황 | 응답 |
|---|---|
| 처음 요청 | `201`, 새 문서 |
| 같은 키, 같은 신청 (기안자·양식·출처·원본 ID가 같음) | `200`, 처음 만든 문서 (제목·결재선이 달라도 처음 문서 그대로) |
| 같은 키, 다른 신청 | `409` `APPROVAL_021` |

### approvalLines 항목

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `empId` | number | O | 결재선에 넣을 사원 ID |
| `step` | number | O | 결재 순서 (1부터) |
| `role` | string | O | `APPROVER`(결재) \| `AGREEMENT`(합의) \| `REFERENCE`(참조) \| `VIEWER`(열람) |

결재선 규칙
- 결재자(`APPROVER`)가 최소 1명 있어야 합니다.
- 기안자 본인은 넣을 수 없습니다.
- 같은 사원을 두 번 넣을 수 없습니다.
- 같은 `step`을 두 번 쓸 수 없습니다.
- 재직 중인 사원만 넣을 수 있습니다.

## 양식별로 보내야 하는 값

휴가·근태 문서는 해당 서비스의 신청을 거쳐야만 만들 수 있습니다. 결재 화면에서 직접 만들 수 없습니다.

| 누가 보내나 | formType | sourceType | sourceId |
|---|---|---|---|
| 결재 화면 (직접 작성) | `GENERAL` 일반 기안 | `DIRECT` 또는 생략 | 보내지 않음 |
| 결재 화면 (직접 작성) | `BUSINESS_TRIP` 출장 신청 | `DIRECT` 또는 생략 | 보내지 않음 |
| leave-service | `LEAVE` 휴가 신청 | `LEAVE` | 휴가 신청 ID (`requestId`) |
| attendance-service | `OVERTIME` 초과근무 신청 | `ATTENDANCE` | 근태 신청 ID |
| attendance-service | `TIME_FIX` 근무시간 수정 신청 | `ATTENDANCE` | 근태 신청 ID |
| attendance-service | `HOLIDAY_SUBST` 휴일대체 신청 | `ATTENDANCE` | 근태 신청 ID |

attendance-service는 신청 종류(`AttendanceRequestType`)와 같은 이름을 `formType`으로 보내면 됩니다.

## 요청 예시

### 결재 화면에서 일반 기안

```json
{
  "title": "프로젝트 예산 집행 요청",
  "formType": "GENERAL",
  "approvalLines": [
    { "empId": 12, "step": 1, "role": "APPROVER" },
    { "empId": 30, "step": 2, "role": "APPROVER" },
    { "empId": 45, "step": 3, "role": "REFERENCE" }
  ]
}
```

### leave-service에서 휴가 결재 요청

```json
{
  "title": "연차 신청 (2026-11-10 ~ 2026-11-11)",
  "formType": "LEAVE",
  "sourceType": "LEAVE",
  "sourceId": 42,
  "requestKey": "3f6b2c1e-8d4a-4b7f-9c2e-1a5d7e9f0b12",
  "approvalLines": [
    { "empId": 12, "step": 1, "role": "APPROVER" }
  ]
}
```

### attendance-service에서 초과근무 결재 요청

```json
{
  "title": "초과근무 신청 (2026-11-05 19:00 ~ 22:00)",
  "formType": "OVERTIME",
  "sourceType": "ATTENDANCE",
  "sourceId": 7,
  "requestKey": "b9e4a7d2-5c1f-4e8a-a3b6-2d7c9f1e4a50",
  "approvalLines": [
    { "empId": 12, "step": 1, "role": "APPROVER" }
  ]
}
```

## 응답

`201 Created` (같은 `requestKey`로 다시 보낸 경우 `200 OK`, 본문 형식은 같음)

```json
{
  "success": true,
  "data": {
    "documentId": 128,
    "docNo": "LEAVE-2026-0001"
  }
}
```

| 필드 | 설명 |
|---|---|
| `documentId` | 결재 문서 ID. 이후 조회·결재 API 경로에 씁니다 (`/api/v1/documents/{documentId}`) |
| `docNo` | 문서번호. 화면 표시용. 형식: `{양식}-{연도}-{일련번호 4자리}`. 양식·연도(한국 시간 기준)별로 1번부터 매깁니다 |

신청 쪽 서비스는 `documentId`를 저장해 두면 나중에 결재 문서를 찾을 수 있습니다.

## 요청 키로 문서 조회

생성 결과를 모를 때(타임아웃 등)나 결재 결과 이벤트를 놓쳤을 때 씁니다.

```
GET /api/v1/documents/by-request/{requestKey}
Authorization: Bearer {access token}
```

| 응답 | 의미 |
|---|---|
| `200` | 문서 상세 (`documentId`, `docNo`, `status`, `requestKey`, 결재선, 이력 등) |
| `404` `APPROVAL_001` | 이 키로 만든 문서가 없음. 또는 조회 권한 없음(기안자·결재선 참여자·관리자만 조회 가능) |

`404`면 문서가 만들어지지 않은 것이 확정입니다. 같은 `requestKey`로 생성을 다시 요청하면 됩니다.

## 오류

| HTTP | 코드 | 원인 |
|---|---|---|
| 400 | `COMMON_001` | 필수 값 누락, 잘못된 enum 값 등 |
| 400 | `APPROVAL_002` | 결재선에 기안자 본인이 있음 |
| 400 | `APPROVAL_003` | 결재선에 같은 사원이 중복됨 |
| 400 | `APPROVAL_004` | 결재 순서(`step`)가 중복됨 |
| 400 | `APPROVAL_011` | 결재선에 결재자(`APPROVER`)가 없음 |
| 400 | `APPROVAL_015` | 존재하지 않는 사원 |
| 400 | `APPROVAL_016` | 재직 중이 아닌 사원 |
| 400 | `APPROVAL_018` | `DIRECT`가 아닌데 `sourceId`가 없음, 또는 `DIRECT`인데 `sourceId`가 있음 |
| 400 | `APPROVAL_019` | 위 표와 다른 양식·출처 조합 (예: 결재 화면에서 `LEAVE` 양식 직접 작성) |
| 400 | `APPROVAL_020` | `DIRECT`가 아닌데 `requestKey`가 없음 |
| 409 | `APPROVAL_021` | 같은 `requestKey`로 다른 신청의 문서가 이미 있음 |
| 401 | `AUTH_xxx` | 토큰이 없거나 유효하지 않음 |
| 503 | `APPROVAL_017` | 사원 정보를 확인할 수 없음 (user-service 장애). 잠시 후 재시도 |

## 아직 지원하지 않는 것

- 결재가 끝났을 때 원본 신청 서비스에 결과를 알려주는 기능 (Kafka 이벤트로 결정, 구현 예정)

다른 양식이나 출처가 필요하면 approval-service 담당자에게 알려 주세요.
