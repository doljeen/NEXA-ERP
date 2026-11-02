# 결재 연동 결정 문서

> 상태: **논의 중** — D1 확정·approval 발행 구현 완료(수신은 각 서비스), D2 구현 완료, D3·D4 논의 전
> 작성: approval-service 담당 / 대상: leave-service, attendance-service, 프런트엔드, infra 담당
> 각 항목의 **권장안**은 제안입니다. 논의 후 `결정` 칸을 채웁니다.

## 배경

결재 문서 생성까지는 연동돼 있습니다. 문서에 출처(`sourceType`, `sourceId`)를 저장하고, 휴가·근태 양식은 해당 서비스의 신청을 거쳐야만 만들 수 있습니다(`DOCS/approval-document-source.md`).

남은 문제는 **결재 이후**입니다.

- 휴가 문서가 승인돼도 leave-service는 그 사실을 모릅니다. 연차 차감이나 신청 상태 변경이 일어나지 않습니다.
- leave-service가 결재 생성을 재시도하면 문서가 두 개 생길 수 있습니다.
- 신청을 취소했을 때 결재 문서를 어떻게 할지 정해져 있지 않습니다.

### 현재 코드에서 확인한 사실

| 항목 | 내용 |
|---|---|
| leave-service 신청 추적 키 | 신청마다 `approvalRequestId`(UUID)를 만들어 저장 (`LeaveRequest.approvalRequestId`) |
| leave-service 결재 호출 흐름 | 결과를 알 수 없는 상태(`UNKNOWN`, `IN_PROGRESS`)면 다시 생성하지 않고 **UUID로 결과를 조회**하도록 설계됨 (`ApprovalClient.findByRequestId`, 아직 미구현) |
| leave-service가 기대하는 조회 결과 | `SUBMITTED_CONFIRMED`(문서 있음), `NOT_CREATED_CONFIRMED`(문서 없음 확정), `UNKNOWN` |
| 메시지 큐 | 현재 어느 서비스에도 Kafka 의존성·브로커 없음 → **새로 구축 필요** |
| approval 결재 상태 | `INPROGRESS` → `APPROVED` / `REJECTED` / `CANCEL`. 반려 문서는 재기안 가능 |

---

## D1. 결재 결과를 원본 서비스에 어떻게 알릴까 — ✅ 확정: Kafka

**결정**: 결재 결과(approval → 원본 서비스)는 **Kafka 이벤트**로 전달한다. (2026-11-02)

검토했던 선택지

| 선택지 | 장점 | 단점 |
|---|---|---|
| A. HTTP 콜백 | 인프라 추가 없음, 즉시 반영 | 호출 실패 시 누락 가능, 수신자가 늘면 approval이 모두 알아야 함 |
| **B. Kafka 이벤트 (채택)** | 수신자가 늘어도 approval 변경 없음, 수신 서비스가 멈춰도 메시지 보관 | 브로커 구축·운영, 발행 신뢰성(아웃박스)·멱등 처리·DLQ 필요 |
| C. 원본 서비스가 조회(폴링) | approval은 조회 API만 제공 | 반영 지연, 불필요한 호출 |

### 호출 방향별 방식

| 방향 | 내용 | 방식 | 이유 |
|---|---|---|---|
| 원본 → approval | 결재 문서 생성 | **HTTP 유지** | 생성 직후 `documentId`를 바로 받아야 함. leave의 UUID 조회 흐름과도 맞음 |
| 원본 → approval | 결재 문서 취소 (D3) | **HTTP 유지** | 취소 성공 여부를 바로 알아야 함 |
| approval → 원본 | 결재 결과 (승인·반려·취소) | **Kafka** | 이번 결정 |
| 원본 → approval | 결과 조회 (복구용) | **HTTP** (D2 조회 API) | Kafka 장애·유실 시 상태를 맞추는 수단으로 유지 |

### 세부 설계 추천안

| 질문 | 추천 | 상태 |
|---|---|---|
| 토픽 구성 | 토픽 하나 `approval.document.result`, 파티션 3, 메시지 키 `documentId` | 추천 |
| 보낼 상태 | 최종 결과만: `APPROVED` / `REJECTED` / `CANCEL` | 추천 |
| 반려 후 재기안 | **출처가 있는 문서(휴가·근태)는 재기안 금지, 반려가 최종.** 다시 하려면 원본에서 새로 신청. 직접 기안은 지금처럼 재기안 허용 | 추천, **leave·attendance 합의 필요** |
| 수신 측 멱등 처리 | **상태 기반**: 신청이 결재 대기 상태일 때만 반영, 이미 반영됐으면 무시. 처리 이력이 필요할 때만 `eventId` 기록 | 추천, **leave·attendance가 결정** |
| 이벤트 형식 공유 | 공통 라이브러리 없음. 서비스마다 DTO, 계약은 `DOCS` 문서로 관리, `eventVersion` 포함, 모르는 필드 무시 | 추천 |
| 재시도·DLQ | 수신 측 `DefaultErrorHandler` 3회 재시도(1s→2s→4s) 후 `approval.document.result.DLT`. DLT는 로그로 남기고 D2 조회 API로 수동 복구 | 추천 |
| approval 발행 | 아웃박스 테이블 + 1초 주기 발행기(`FOR UPDATE SKIP LOCKED`), 프로듀서 `acks=all`·idempotence, 발행 완료 행 7일 후 삭제 | 추천 |
| 브로커 | approval-service `compose.yaml`에 `apache/kafka:3.9.1` KRaft 단일 노드, 호스트 포트 19092. 토픽은 approval 앱이 `NewTopic`으로 생성(자동 생성 끔) | **담당: approval 담당자**. 공용 환경(루트 compose, config-server)과 leave·attendance 접속 설정은 각 담당자 |

**토픽**

- 토픽 하나 `approval.document.result`. 각 서비스는 자기 `sourceType`만 처리합니다.
- 출처별 토픽(`approval.document.result.leave` 등)은 서비스가 다른 출처의 메시지를 받지 않는 장점이 있지만, 출처가 늘 때마다 토픽을 추가해야 해서 택하지 않았습니다.
- 메시지 키: `documentId` → 같은 문서의 이벤트는 같은 파티션으로 가서 **순서가 보장**됩니다.

**이벤트 형식**

```json
{
  "eventId": "c2f0...-uuid",          // 이벤트마다 고유. 수신 측 중복 처리 판단용
  "eventType": "DOCUMENT_COMPLETED",
  "occurredAt": "2026-11-03T10:15:00+09:00",
  "documentId": 128,
  "docNo": "LEAVE-2026-0001",
  "formType": "LEAVE",
  "sourceType": "LEAVE",
  "sourceId": 42,
  "requestKey": "8f1c...-uuid",        // D2의 요청 키
  "status": "APPROVED"                 // APPROVED | REJECTED | CANCEL
}
```

**발행 신뢰성 (approval)** — 필수

- 결재 처리와 이벤트 발행을 원자적으로 묶기 위해 **트랜잭셔널 아웃박스**를 씁니다.
  1. 결재 상태 변경과 같은 트랜잭션에서 `approval_outbox` 테이블에 이벤트를 저장
  2. 별도 발행기가 아웃박스를 읽어 Kafka로 보내고, 성공하면 발행 완료로 표시
- 이렇게 하지 않으면 "DB는 승인됐는데 이벤트는 안 나간" 상태가 생길 수 있습니다.
- 결과: 이벤트는 **최소 한 번** 전달됩니다(중복 가능).

**수신 신뢰성 (leave, attendance)** — 필수

- **멱등 처리**: 같은 이벤트가 두 번 와도 결과가 같아야 합니다. `eventId` 처리 기록이나, 신청 상태를 보고 이미 반영됐으면 무시하는 방식으로 처리합니다.
- **재시도와 DLQ**: 처리에 실패하면 몇 번 재시도하고, 그래도 실패하면 `approval.document.result.DLT`로 보내 따로 확인합니다.
- **컨슈머 그룹**: 서비스마다 하나 (`leave-service`, `attendance-service`)

**복구**

- Kafka가 장애이거나 메시지가 DLQ로 빠지면, 원본 서비스가 D2 조회 API로 상태를 맞춥니다.

**인프라**

- 개발 환경: approval-service `compose.yaml`에 KRaft 단일 브로커 (approval 담당자)
- 공용 개발·운영 환경 반영, 서비스별 접속 설정(`spring.kafka.bootstrap-servers`)은 config-server로 관리 (각 담당자)
- 테스트: Testcontainers Kafka

### 구현 현황 (approval-service) — ✅ 발행 쪽 완료

| 항목 | 구현 |
|---|---|
| 아웃박스 | `V7__approval_outbox.sql` (`approval_outbox`, 미발행 부분 인덱스) |
| 이벤트 기록 | 최종 승인·전결·반려·회수 직후 같은 트랜잭션에서 기록. 출처 문서만, 중간 결재는 기록 안 함 |
| 발행기 | 1초 주기, `FOR UPDATE SKIP LOCKED`, 브로커 확인 후 발행 완료 표시, 실패 시 그 자리에서 멈춤(순서 유지), 발행 완료 행 7일 후 삭제 |
| 프로듀서 | `acks=all`, idempotence, `max.block.ms=5s`, `delivery.timeout.ms=10s` |
| 토픽 | 앱 기동 시 `approval.document.result`, `.DLT` 생성 (파티션 3) |
| 브로커 | approval-service `compose.yaml` `kafka` (`apache/kafka:3.9.1`, 호스트 `localhost:19092`) |
| 테스트 | Testcontainers Kafka 통합 4개(롤백 시 이벤트 없음 포함), 발행기 단위 3개 |

수신 측 계약과 예시 코드: `DOCS/approval-result-event.md`

### 남은 논의

- [ ] 출처가 있는 문서의 재기안 금지 (leave·attendance 합의)
- [ ] 수신 측 멱등 처리 방식 (leave·attendance 결정)
- [ ] DLT 메시지를 누가 언제 확인할 것인가
- [ ] 공용 환경에 브로커를 올리는 일정 (infra)

---

## D2. 중복 생성 방지와 결과 조회 계약 — ✅ 구현 완료 (계약 확인 필요)

**구현한 계약** (approval-service, V6 마이그레이션)

- 문서 생성 요청에 `requestKey`(UUID)를 받습니다. leave의 `approvalRequestId`를 그대로 넣으면 됩니다.
  - 출처가 있는 문서(`LEAVE`/`ATTENDANCE`)는 **필수** → 없으면 400 `APPROVAL_020`
  - 직접 기안은 선택 (프런트 중복 클릭 방지용으로 써도 됨)
- 같은 `requestKey`로 다시 요청하면:
  - 같은 신청(기안자·양식·출처·원본 ID가 같음)이면 **새로 만들지 않고 처음 문서를 `200`으로** 돌려줍니다. 최초 생성은 `201`
  - 다른 신청이면 409 `APPROVAL_021`
  - 제목·결재선은 재시도 중 바뀔 수 있어 비교하지 않습니다
- 같은 키로 동시에 요청이 와도 PostgreSQL advisory lock으로 하나씩 처리해 문서는 하나만 생깁니다.

```
GET /api/v1/documents/by-request/{requestKey}
200 → 문서 상세 (documentId, docNo, status, requestKey, 결재선, 이력 ...)
404 (APPROVAL_001) → 이 키로 만든 문서가 없음 (leave의 NOT_CREATED_CONFIRMED)
                     또는 조회 권한 없음 (기안자·결재선 참여자·관리자만 조회 가능)
```

**확인할 질문**

- [ ] 필드 이름 `requestKey`로 괜찮은가 (leave는 `approvalRequestId`)
- [ ] 404를 "문서 없음 확정"으로 써도 되는가 (approval 장애는 5xx로 구분됨)
- [ ] attendance-service도 신청마다 UUID를 만들어 같은 방식으로 쓸 수 있는가

---

## D3. 신청 취소와 결재 문서 연동

**결정할 것**: 원본 신청을 취소하거나 결재 문서를 취소했을 때 반대편을 어떻게 맞출지

| 선택지 | 설명 |
|---|---|
| A. 원본 서비스에서 취소 → 결재 문서도 취소 | 원본 서비스가 approval 취소 API(HTTP) 호출 |
| B. 결재 화면에서만 취소 → 원본에 알림 | D1 이벤트의 `CANCEL` 상태 사용 |
| C. A와 B 모두 허용 | 어느 쪽에서 취소해도 반대편이 따라감 |

**권장안: C, 단 결재 진행 중(`INPROGRESS`)일 때만**

- 원본에서 취소하면 approval 취소 API를 호출합니다.
- 결재 화면에서 취소하면 Kafka 이벤트(`status: CANCEL`)로 원본에 알립니다.
- 원본이 취소를 요청한 경우에도 `CANCEL` 이벤트가 나갑니다. 원본 서비스는 이미 취소된 신청이면 무시합니다(멱등).
- 이미 승인된 뒤의 취소(승인된 휴가 철회 등)는 연차 복구 같은 후속 처리가 필요하므로 **이번 범위에서 제외**하고 별도로 다룹니다.

**논의할 질문**

- [ ] 원본 서비스가 결재 문서를 취소할 때 누구 권한으로 호출하는가 (신청자 토큰 / 서비스 간 인증)
- [ ] 승인 후 취소가 필요한가, 필요하다면 언제 다룰 것인가
- [ ] 양쪽에서 동시에 취소하는 경우: 늦게 온 쪽은 "이미 취소됨"을 성공으로 볼 것인가

**결정**: (미정)

---

## D4. 서비스 간 호출 인증과 출처 지정 권한

**결정할 것**: 원본 → approval HTTP 호출(문서 생성·취소)을 어떻게 인증할지, 그리고 `LEAVE`/`ATTENDANCE` 출처를 아무나 지정하지 못하게 막는 방법

**현재 상태**: 문서 생성은 사용자 토큰으로 호출하며, 일반 사용자도 `sourceType: LEAVE`를 넣어 보낼 수 있습니다.
결과 알림은 Kafka로 정해져 approval → 원본 HTTP 콜백은 필요 없습니다. Kafka 브로커는 내부망에서만 접근하도록 둡니다.

| 선택지 | 설명 |
|---|---|
| A. 사용자 토큰 전달 + 출처는 신뢰 | 지금과 같음. 위조 가능 |
| B. 서비스 전용 토큰(클라이언트 자격 증명) | 서비스 간 호출에만 서비스 토큰 사용, 출처 지정은 서비스 토큰일 때만 허용 |
| C. 내부 경로(`/internal/**`)를 게이트웨이에서 외부 차단 | 내부 경로는 클러스터 안에서만 호출 가능 |

**권장안: C를 먼저, 필요하면 B 추가**

- 출처가 있는 문서 생성과 취소 연동은 `/internal/**` 경로로 두고 게이트웨이에서 외부 접근을 막습니다.
- 기안자는 여전히 신청자이므로 요청에 신청자 ID를 담거나 사용자 토큰을 함께 전달합니다.
- 보안 요구가 높아지면 서비스 전용 토큰(B)을 추가합니다.

**논의할 질문**

- [ ] 게이트웨이에서 `/internal/**` 차단이 가능한가 (gateway/infra 담당)
- [ ] 내부 호출 시 기안자(신청자)를 어떻게 전달할 것인가

**결정**: (미정)

---

## 메시지로 확인할 항목

| # | 항목 | 대상 | 질문 | 결정 |
|---|---|---|---|---|
| M1 | `AttendanceRequest.docNo`(`Long`) | attendance | approval의 `documentId`를 저장하려는 필드인가? 맞다면 `approvalDocId`로 이름 변경 제안 | |
| M2 | 결재 화면 양식 제한 | 프런트 | 휴가·근태 양식 직접 작성 막기, `TIME_FIX`/`HOLIDAY_SUBST` 라벨 추가 | |
| M3 | 출장 신청 출처 | 기획 | 출장은 결재 화면 직접 작성으로만 둘 것인가 | |

---

## 진행 순서

1. 이 문서 공유 → 각 담당자가 질문에 의견 남기기
2. 회의로 D1 남은 논의(재기안·멱등 방식), D3, D4 결정 (D2 확인, M1~M3는 메시지로)
3. 구현
   - approval: ~~D2 요청 키·조회 API~~ (완료) → ~~브로커(compose) + 아웃박스 + Kafka 발행(D1)~~ (완료) → 출처 문서 재기안 금지 → 취소 API(D3)
   - leave/attendance: 생성 요청에 `requestKey` 추가, 조회 클라이언트(D2) → Kafka 수신·멱등 처리·DLQ(D1) → 취소 호출(D3)

## 결정 기록

| 날짜 | 항목 | 결정 | 비고 |
|---|---|---|---|
| 2026-11-02 | D1 | 결재 결과 알림은 Kafka 이벤트. 문서 생성·취소(원본 → approval)는 HTTP 유지, D2 조회 API는 복구용으로 유지 | 아웃박스·멱등 수신·DLQ 필수 |
| 2026-11-02 | D1 | 브로커 구축은 approval 담당자가 approval-service compose 범위에서 진행. 세부 설계는 추천안 기준 | 재기안 금지·멱등 방식은 합의 필요 |
| 2026-11-02 | D2 | `requestKey` 구현: 출처 문서 필수, 같은 신청 재요청은 200으로 기존 문서, 다른 신청은 409, `GET /by-request/{key}` 조회 | 필드 이름·404 의미는 leave 확인 필요 |
| 2026-11-02 | D1 | approval 발행 구현: 아웃박스(V7) + 1초 발행기 + 토픽 생성 + compose 브로커 | 수신 가이드 `DOCS/approval-result-event.md` |
