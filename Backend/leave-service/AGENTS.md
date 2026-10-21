# leave-service 코드 작성 규칙

## 1. 적용 범위와 우선순위

- 이 지침은 `Backend/leave-service` 내부 작업에 적용한다. 해당 디렉터리 외부 파일은 수정하지 않는다.
- 우선순위는 사용자의 명시적 작업 지시 → Nexa의 기존 API 계약·비즈니스 규칙·환경 제약 → 이 문서의 가독성 규칙 → SOBI 참고 패턴 순서다.
- 일반적인 Spring Boot 관례보다 실제로 확인된 SOBI 작성 패턴을 우선 참고하되, 가독성이나 안정성을 떨어뜨리는 방식은 그대로 복제하지 않는다.
- SOBI 기능 구조를 Nexa에 그대로 옮기지 않는다. 현재 기능에 필요한 구조만 사용한다.

## 2. 기존 코드 변경 최소화

- 스타일 통일만을 목적으로 기존 정상 동작 코드를 일괄 리팩터링하지 않는다.
- 새로운 기능을 구현하거나 기존 기능을 수정하는 범위에서 가독성 규칙을 우선 적용한다.
- 작업과 무관한 기존 코드 문제를 발견하면 먼저 사용자에게 알리고, 임의로 수정하지 않는다. 요청된 수정에 필요한 변경은 승인된 범위 안에서 수행한다.
- 가독성 개선만으로 API 응답, 권한, 정렬, null 처리, 예외 발생 우선순위, 업무 상태 전이, 트랜잭션 의미를 변경하지 않는다.
- 별도 요청 없이 기존 Java 코드, DTO 형태, 인터페이스 구조를 일괄 변경하지 않는다.

## 3. SOBI 분석 근거와 작성자 구분

### 3.1 분석 기준

- 참고 저장소: `S15P21D101`의 SOBI Java/Spring Boot 백엔드.
- 분석 기준 커밋: `b8e673734a18565bc6e3b43f7953bd621376719f`.
- 아래 근거 경로는 SOBI 저장소 루트 기준이다.
- 분석 당시 Git 사용자 설정과 README 담당 영역을 바탕으로 `Sunghyun Park` 작성 기록을 사용자 개인 스타일의 우선 참고 대상으로 삼았다.
- Git 작성자 기록은 실제 타이핑 주체나 공동 작업 여부까지 보증하지 않는다. 작성자 미확인 코드나 공동 수정 파일 전체를 개인 스타일로 단정하지 않는다.
- 참고 소스를 찾을 수 없으면 임의의 다른 프로젝트를 SOBI 대신 분석하지 말고 저장소 경로나 소스 제공을 요청한다.

### 3.2 개인 작성 기록에서 실제 확인한 패턴

| 근거 파일 | 확인한 패턴 | Git 근거 |
| --- | --- | --- |
| `backend/src/main/java/com/sobi/document/write/service/DocumentWriteServiceImpl.java` | 입력 검증 → 문서 조회 → 작성 가능 여부 확인 → 템플릿 조회 → 초안 생성 → 응답 정합성 확인 → 다운로드 → 결과 반환. 실패 조건에서 즉시 예외 발생. | 현재 줄별 작성 기록 Sunghyun Park, 주요 커밋 `6232223` |
| `backend/src/main/java/com/sobi/document/write/client/DocumentAgentClient.java` | HTTP 통신과 응답 검증 분리, 외부 오류를 업무 오류로 변환, `mapFailure` 등 private 메서드, 민감한 오류 본문을 기록하지 않는 이유 주석. | 현재 줄별 작성 기록 Sunghyun Park, 주요 커밋 `6232223` |
| `backend/src/main/java/com/sobi/document/write/controller/DocumentWriteController.java` | 업무 처리는 Service에 위임하고 Controller에서 HTTP 응답과 다운로드 헤더 구성. | 주요 구현 커밋 `6232223`, 작성자 Sunghyun Park |
| `backend/src/main/java/com/sobi/document/write/repository/CompletedDocumentTemplateQuery.java` | 필요한 ID만 조회, SQL Text Block, 조회 책임 분리, 다른 시스템의 Entity를 복제하지 않는 이유 주석. | 현재 줄별 작성 기록 Sunghyun Park, 주요 커밋 `6232223` |
| `backend/src/main/java/com/sobi/funding/service/FundingService.java` | `targetAmount`, `candidates`, `combinations` 등 중간 변수, 일반 for문, 후보 수집과 변환을 의미 있는 메서드로 분리. | 현재 줄별 작성 기록 Sunghyun Park |
| `backend/src/main/java/com/sobi/dashboard/service/DashboardServiceImpl.java` | Guard Clause, 사용자 유형별 흐름 분리, 매출·요약 계산 단위의 private 메서드, 논리 단계별 빈 줄, 명시적인 목록 생성. | 현재 줄별 작성 기록 Sunghyun Park |
| `backend/src/main/java/com/sobi/funding/dto/FundingCombinationResponse.java` | `from(...)` 안에서 Builder로 DTO 생성, 단순 목록 변환에는 `map(...).toList()` 사용. | 해당 변환 코드 작성자 Sunghyun Park, 커밋 `fd08e0b` |

개인 작성 기록에서 반복되는 핵심은 업무 의미를 드러내는 이름, 명시적인 중간 변수, 일반 반복문, Guard Clause, 업무 단위 메서드 분리다. Stream 금지, 모든 Service의 인터페이스 분리, 모든 DTO의 Builder 사용은 실제 코드에서 확인된 일관된 규칙이 아니다.

### 3.3 팀 코드에서 확인한 참고 사례

| 근거 파일 | 확인한 패턴 | 작성자 구분 |
| --- | --- | --- |
| `backend/src/main/java/com/sobi/application/service/ApplicationDocumentServiceImpl.java` | 권한·상태 확인 후 파일 처리, 커밋·롤백에 따른 파일 정리, 검증 이벤트 발행, 이유 중심 주석. | 현재 줄별 작성 기록 권병수 |
| `backend/src/main/java/com/sobi/application/service/DocumentValidationProcessor.java` | 커밋 후 비동기 검증, 외부 호출 동안 DB 트랜잭션 미유지, 검증 결과 저장 전 조건부 상태 전이. | 현재 줄별 작성 기록 권병수 |
| `backend/src/main/java/com/sobi/application/repository/ApplicationDocumentRepository.java` | 파일 경로와 현재 상태를 조건으로 갱신하여 늦게 도착한 검증 결과의 덮어쓰기 방지. | 팀 코드의 Repository 책임 분리 사례이며 개인 스타일로 분류하지 않음 |
| `backend/src/main/java/com/sobi/application/entity/ApplicationDocument.java` | `notSubmitted(...)` 생성 팩토리, `upload(...)` 상태 변경, `isValidating()` 업무 조건 표현. | 파일은 공동 수정. 나열한 메서드는 권병수 작성 기록 |
| `backend/src/main/java/com/sobi/application/dto/ApplicationDocumentResponse.java` | `from(...)` 변환과 연결 대상에 따른 이름 선택. | 공동 수정. 권병수 작성 기록 43줄, Sunghyun Park 4줄이며 DTO 전체를 개인 스타일로 분류하지 않음 |

줄별 작성 기록의 줄 수는 빈 줄과 주석을 포함한다. 팀 코드 사례는 기술적 필요가 있는 경우 참고하며, Nexa에 비동기 이벤트나 별도 Query 클래스를 의무적으로 추가하는 근거로 사용하지 않는다.

### 3.4 실제 패턴과 개선 규칙의 구분

- 실제 SOBI 코드에는 짧은 필드명, 긴 복합 조건, 한 줄 조건문, 과도한 빈 줄, 단순 동작 설명 주석, 주석 처리된 과거 코드도 혼재한다.
- 이를 개인의 필수 스타일로 복제하지 않는다.
- 아래의 중괄호 사용, 빈 줄 하나로 단계 구분, 구체적인 의존성 이름, 과도한 메서드 추출 방지 등의 규칙은 사용자 요청과 일반적인 가독성 원칙을 반영한 개선 규칙이다.
- SOBI에서 확인되지 않은 권장 사항을 실제 SOBI 관례로 설명하지 않는다.

## 4. 네이밍과 상위 메서드 구성

- 변수명은 업무 대상과 의미를 드러내고 불필요한 축약을 피한다. `employeeId`, `leaveTypes`, `remainingMinutes`처럼 문맥에 맞는 이름을 사용한다.
- 의존성은 `leaveTypeRepository`, `employeeAccess`처럼 역할과 대상을 드러내는 이름을 사용한다.
- 메서드는 동작과 대상을 표현한다. Boolean 조회는 문맥에 맞게 `is`, `has`, `can` 등을 사용한다.
- 조회·계산 결과에 의미 있는 이름을 붙여 흐름을 드러낸다. 의미 없는 중간 변수는 늘리지 않는다.
- 상위 메서드에서 전체 비즈니스 흐름이 자연스럽게 보이도록 작성한다.
- 여러 단계의 로직은 검증 → 조회 → 처리 → 저장 → 응답 흐름을 참고하되, 실제 데이터 의존 관계와 기존 처리 순서를 우선한다.
- 조회 전용 로직에 불필요한 처리·저장 단계를 만들지 않는다.
- 메서드 길이를 기계적으로 제한하지 않는다. 처음 보는 개발자가 위에서 아래로 읽으며 이해할 수 있는 구조를 우선한다.

## 5. 조건문·예외 처리·메서드 분리

- 실패 조건은 Guard Clause로 처리하고 불필요한 중첩을 줄인다.
- 조건문과 반복문에는 중괄호를 사용한다.
- 긴 복합 조건은 업무 의미가 드러나는 변수나 메서드로 나눈다. 단락 평가와 null 안전성을 유지한다.
- 복잡한 검증, 계산, 상태 변경 조정은 의미 있는 private 메서드로 분리한다. Entity 자체의 상태 변경 책임은 Entity의 업무 메서드로 유지할 수 있다.
- 단순한 조건문이나 한두 줄 코드를 무조건 분리하지 않는다. 추출 후 호출 이름이 의도를 더 잘 설명하는지 판단한다.
- 한 번의 호출을 감싸는 메서드, 지나치게 긴 호출 사슬, 잘게 쪼개져 전체 흐름을 따라가기 어려운 구조를 만들지 않는다.
- 가능한 한 주요 진입점 다음에 관련 private 메서드가 이어지도록 구성한다.
- 업무 오류는 기존 `BusinessException`과 `ErrorCode`로 표현하고 전역 예외 처리에 맡긴다.
- 기존 계약에서 요구하지 않는 한 예외를 성공 응답, 빈 목록, null로 바꾸지 않는다.
- 외부 통신 오류와 업무상 실패 결과는 해당 연동 계약에 따라 구분한다. SOBI OCR의 실패 상태 반환 방식을 모든 기능에 일반화하지 않는다.

## 6. 계층 책임과 데이터 정합성

- Controller는 요청 매핑, 입력 검증, Service 위임, HTTP 응답 구성을 담당한다.
- Service는 권한·업무 검증과 여러 작업의 처리 순서를 조정한다.
- Repository는 조회, 저장, 필요한 원자적 갱신을 담당한다. HTTP 호출이나 API 응답 생성을 넣지 않는다.
- Entity는 필요한 경우 업무 의미가 있는 상태 변경 메서드를 제공한다. Service에서 여러 setter를 흩어 호출하기보다 상태 변경 의도가 드러나도록 작성한다.
- 여러 Entity의 상태가 변경되면 변경 순서, 각 변경의 책임, 중간 실패 시 처리와 원자성 범위를 명확히 한다.
- DTO 변환 메서드는 단순 변환을 담당한다. DB 조회나 외부 호출을 수행하지 않는다.
- 트랜잭션, 동시성, 데이터 정합성에 영향을 주는 코드는 간결함보다 정확성과 이해하기 쉬운 흐름을 우선한다.
- 외부 호출과 DB 트랜잭션 경계, 재시도·중복 처리·늦은 응답의 영향을 실제 업무에 맞게 검토한다. SOBI의 구조를 기계적으로 복사하지 않는다.
- 기존 구조에 필요하지 않은 인터페이스, 추상화, 디자인 패턴을 추가하지 않는다. Service/ServiceImpl 분리를 의무화하지 않는다.

## 7. 주석·빈 줄·객체 생성·반복문

- 주요 처리 단계를 논리적으로 나누고 빈 줄 하나로 구분한다. 모든 문장 사이에 빈 줄을 넣지 않는다.
- 비즈니스 규칙, 처리 이유, 예외적인 제약, 상태 변경 순서의 이유를 설명하는 주석은 적극적으로 사용한다.
- 단순히 코드 내용을 반복하는 주석과 주석 처리된 과거 코드는 남기지 않는다.
- 일반 반복문으로 분기·누적·상태 변경이 더 명확해지면 반복문을 사용한다.
- Stream 사용을 금지하지 않는다. 일반 반복문보다 명확하면 사용하고, 단순한 `map(...).toList()`도 허용한다.
- Stream, Optional, 삼항 연산자를 무리하게 연결해 여러 업무 단계를 한 줄에 압축하지 않는다.
- 짧고 명확한 기본값 선택에는 삼항 연산자를 사용할 수 있다.
- DTO의 기존 record·클래스 형태를 유지한다. 단순 변환은 `from(...)` 등 기존 변환 방식에 맞춘다.
- 생성자는 인자가 적고 의미가 명확할 때 사용한다. Builder는 필드 대응을 읽기 쉽게 만드는 경우 사용하며 일괄 강제하지 않는다.
- `var`는 우변에서 타입과 의미가 명확할 때 사용한다.
- 짧은 코드보다 이해하기 쉽고 수정하기 쉬운 코드를 우선한다. 메서드·클래스 분리는 실제 가독성 향상에 도움이 될 때만 수행한다.

## 8. Nexa 환경과 기존 계약 유지

- 기존 `ApiResult<T>`의 `success`, `error`, `data` 응답 규격과 전역 예외 처리 방식을 유지한다.
- SOBI의 `ApiResponse`나 파일 다운로드 응답 예외를 Nexa API에 임의로 도입하지 않는다.
- 기존 Flyway 마이그레이션 파일은 수정하지 않는다. 승인된 기능에 스키마 변경이 필요하면 새 버전 파일을 추가한다.
- HR Service·Approval Service 연동이 필요한 미구현 부분에는 연동 대상과 필요한 작업을 구체적인 TODO로 명시한다.
- TODO만 남기고 임의의 운영용 성공 결과를 반환하지 않는다.
- 개발용 임시 인증은 개발 환경에서만 활성화한다.
- 현재 `DevEmployeeAccess`의 `dev & !prod & !production` 제한과 비개발 환경의 접근 차단을 약화하지 않는다.
- Spring Boot 4.1.1과 Java 21 환경을 유지한다. SOBI의 의존성이나 import를 그대로 복사하지 않는다.

## 9. Nexa 적용 예시

아래는 기존 휴가 유형 조회의 권한·조회·응답 의미를 유지하면서 명시적인 중간 변수와 일반 반복문을 사용하는 예시다. 기존의 짧은 Stream을 반드시 교체하라는 지시가 아니다. 예시를 적용하려면 `java.util.ArrayList` import가 필요하다.

```java
@Transactional(readOnly = true)
public List<LeaveTypeResponse> getAllTypes() {
    Long employeeId = employeeAccess.currentEmployeeId();
    if (!employeeAccess.isAdmin(employeeId)) {
        throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    List<LeaveType> leaveTypes =
            leaveTypeRepository.findAllWithBalanceType();

    List<LeaveTypeResponse> responses = new ArrayList<>();
    for (LeaveType leaveType : leaveTypes) {
        responses.add(LeaveTypeResponse.from(leaveType));
    }

    return responses;
}
```

단순한 관리자 검사와 DTO 추가를 각각 private 메서드로 추출하지 않는다. 단순 변환이 더 잘 읽히면 다음 표현도 허용한다.

```java
List<LeaveTypeResponse> responses = leaveTypes.stream()
        .map(LeaveTypeResponse::from)
        .toList();
```

Controller는 기존 응답 규격을 유지한다.

```java
@GetMapping
public ResponseEntity<ApiResult<List<LeaveTypeResponse>>> getAllTypes() {
    List<LeaveTypeResponse> leaveTypes = leaveTypeService.getAllTypes();
    return ResponseEntity.ok(ApiResult.success(leaveTypes));
}
```

연동 TODO는 필요한 실제 위치에 다음처럼 구체적으로 작성한다. 이 예시는 연동 동작이나 업무 상태 전이를 새롭게 정의하지 않는다.

```java
// TODO: HR Service에서 직원 직책을 조회해 관리자 권한을 판단한다.
// TODO: Approval Service의 결재 요청 API와 휴가 신청을 연계한다.
```

## 10. Codex 필수 체크리스트

- [ ] 수정 파일이 모두 `Backend/leave-service` 내부인가?
- [ ] 요청 범위를 벗어난 스타일 일괄 리팩터링을 하지 않았는가?
- [ ] 작업과 무관하게 발견한 기존 문제를 임의로 수정하지 않고 사용자에게 알렸는가?
- [ ] 기존 API, 업무 규칙, 권한, 정렬, null 처리, 예외 발생 우선순위를 유지했는가?
- [ ] SOBI 개인 작성 패턴, 팀 코드 사례, 일반적인 가독성 개선 규칙을 구분했는가?
- [ ] 변수·메서드 이름만으로 대상과 동작을 이해할 수 있는가?
- [ ] 상위 메서드를 위에서 아래로 읽으면 전체 업무 흐름이 보이는가?
- [ ] 복잡한 검증·계산·상태 변경을 의미 있게 분리하고 단순 코드는 과도하게 추출하지 않았는가?
- [ ] 메서드 길이를 기계적으로 제한하지 않고 이해하기 쉬운 구조를 선택했는가?
- [ ] 주요 처리 단계와 여러 Entity의 변경 순서·책임이 명확한가?
- [ ] 중첩 조건과 긴 표현식을 줄이고 반복문·Stream 중 더 명확한 방식을 선택했는가?
- [ ] 주석은 업무 규칙이나 이유를 설명하고 빈 줄은 논리 단계별로 사용했는가?
- [ ] Controller·Service·Repository·Entity·DTO의 책임을 지켰는가?
- [ ] `ApiResult<T>`와 전역 예외 처리 방식을 유지했는가?
- [ ] 기존 Flyway 마이그레이션을 수정하지 않았는가?
- [ ] 필요한 HR·Approval TODO와 개발 환경에 한정된 임시 인증을 유지했는가?
- [ ] 트랜잭션·외부 호출·동시성·데이터 정합성에 미치는 영향을 확인했는가?
- [ ] Spring Boot 4.1.1·Java 21에서 변경에 필요한 검증을 수행하고 결과와 한계를 설명했는가?
