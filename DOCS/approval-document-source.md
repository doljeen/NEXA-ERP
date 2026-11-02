# [approval-service] 결재 문서 '출처' 추가 + 양식별 생성 규칙

## 요약

1. 결재 문서가 **어디서 만들어졌는지**(어느 서비스의 몇 번 신청인지) 저장합니다.
2. 근태 양식 2개를 추가했습니다: 근무시간 수정(`TIME_FIX`), 휴일대체(`HOLIDAY_SUBST`)
3. 휴가·근태 문서는 이제 **해당 서비스의 신청을 거쳐야만** 만들 수 있습니다.
   결재 화면에서 직접 만든 휴가·근태 문서는 승인돼도 원본 신청에 반영되지 않기 때문입니다.

## API 변경: `POST /api/v1/documents`

요청에 선택 필드 두 개가 추가됐습니다.

```json
{
  "sourceType": "ATTENDANCE",
  "sourceId": 42
}
```

| 필드 | 값 | 설명 |
|---|---|---|
| `sourceType` | `DIRECT` \| `LEAVE` \| `ATTENDANCE` | 생략하면 `DIRECT` |
| `sourceId` | 숫자 | 원본 신청 ID |

- `DIRECT`: 결재 화면에서 사용자가 직접 작성한 문서
- `LEAVE` / `ATTENDANCE`: 해당 서비스의 신청을 통해 만들어진 문서

## 양식별로 허용되는 출처

| formType | 설명 | sourceType |
|---|---|---|
| `LEAVE` | 휴가 신청 | `LEAVE` |
| `OVERTIME` | 초과근무 신청 | `ATTENDANCE` |
| `TIME_FIX` | 근무시간 수정 신청 **(신규)** | `ATTENDANCE` |
| `HOLIDAY_SUBST` | 휴일대체 신청 **(신규)** | `ATTENDANCE` |
| `BUSINESS_TRIP` | 출장 신청 | `DIRECT` |
| `GENERAL` | 일반 기안 | `DIRECT` |

## 오류 (모두 400)

| 코드 | 조건 |
|---|---|
| `APPROVAL_018` | `LEAVE`/`ATTENDANCE`인데 `sourceId`가 없는 경우, 또는 `DIRECT`인데 `sourceId`가 있는 경우 |
| `APPROVAL_019` | 위 표와 다른 양식·출처 조합 |

## 담당자별로 할 일

### leave-service
결재 생성 요청에 `formType=LEAVE`, `sourceType=LEAVE`, `sourceId=휴가 신청 requestId`를 담아 주세요.

### attendance-service
신청 종류(`AttendanceRequestType`)와 같은 이름의 `formType`을 보내 주세요.

| AttendanceRequestType | formType |
|---|---|
| `OVERTIME` | `OVERTIME` |
| `TIME_FIX` | `TIME_FIX` |
| `HOLIDAY_SUBST` | `HOLIDAY_SUBST` |

여기에 `sourceType=ATTENDANCE`, `sourceId=근태 신청 ID`를 함께 담아 주세요.

### 프런트엔드
- 일반 기안, 출장 신청 화면은 수정할 필요가 없습니다(생략하면 `DIRECT`).
- 결재 화면에서 휴가·초과근무 양식을 직접 작성하는 기능이 있다면 이제 400이 나므로, 휴가 신청이나 근태 신청 화면으로 연결해 주세요.
- 문서 목록 필터(`?formType=`)에 `TIME_FIX`와 `HOLIDAY_SUBST`를 쓸 수 있습니다. 라벨 추가가 필요합니다.
- 문서 상세 응답(`GET /api/v1/documents/{id}`)에 `sourceType`과 `sourceId`가 추가됩니다.

## DB

- `approval_document` 테이블에 `source_type`, `source_id` 컬럼이 추가됩니다(Flyway V4).
- 배포하면 자동으로 적용되고, 기존 문서는 `DIRECT`로 채워집니다.
- `form_type`은 문자열 컬럼이라 새 양식 추가에 따른 스키마 변경은 없습니다.
- 양식·출처 규칙은 문서를 **생성할 때만** 검사하므로, 기존 문서를 조회하거나 결재하는 데는 영향이 없습니다.

## 아직 안 된 것 (추후 공유)

- 결재가 끝났을 때 원본 서비스(leave/attendance)에 결과를 알려주는 기능
- 같은 신청이 재시도로 두 번 들어올 때 문서 중복 생성 방지
- 출처 값은 아직 요청한 쪽이 보낸 대로 저장합니다(서비스 간 호출 전용 제한 없음)

다른 출처나 양식이 필요하면 알려 주세요. 예를 들어 출장을 별도 서비스에서 신청하게 되거나 payroll-service에서 결재가 필요해지면 그에 맞게 추가하겠습니다.

MR: (머지 후 링크)
