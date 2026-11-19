<div align="center">
  <img src="DOCS/logo-2048.jpg" width="180" alt="NEXA 로고">
  <h1>NEXA</h1>
  <p><strong>AI 업무 자동화 HR ERP</strong></p>
  <p>인사, 근태, 휴가, 전자결재, 급여를 하나로 연결하고<br>엑셀 데이터 이관과 AI 업무 자동화를 지원하는 서비스입니다.</p>
  <p>SSAFY 자율 프로젝트 | 6명 | 8주<br><strong>강석진 : Infrastructure / CI/CD</strong></p>
</div>

<br>

> 이 저장소는 팀이 SSAFY GitLab에서 함께 개발한 NEXA의 소스를 옮긴 것입니다.
> 제품 기능은 팀 전체의 결과물이고 **아래 트러블슈팅은 제가 맡은 인프라와 배포 작업**입니다.
> 수치는 개발 서버의 Runner 로그, `sar` CPU 기록, Nginx 접속 로그에서 다시 확인한 값입니다. ([측정 방법](#측정-방법))

**바로가기** : [성과 요약](#성과-요약) | [트러블슈팅](#트러블슈팅) | [측정 방법](#측정-방법) | [아키텍처](#아키텍처) | [CI/CD](#cicd-흐름) | [상세 기록](DOCS/infra-troubleshooting.md)

<br>

## 성과 요약

| 문제 | 한 일 | 결과 |
| --- | --- | --- |
| 배포할 때마다 Java 코드를 다시 컴파일 | CI가 만든 JAR를 배포에 그대로 사용 | 서비스 7개 배포 시간 **평균 1~3분 → 30초 안팎** |
| CI마다 Gradle 의존성을 새로 다운로드 | 서비스별 Gradle 캐시 적용 | User 빌드 **2분 38초 → 1분 35초 (약 40% 단축)** |
| 빌드가 몰리면 개발 서버 CPU가 95%까지 상승 | 개발 서버와 운영 서버 분리 | 운영 서버에는 빌드와 모니터링 도구 **0개** |
| 관리자 DB 계정 하나를 모든 서비스가 공유 | 서비스마다 DB와 계정을 따로 생성 | 다른 서비스 DB **접속 차단** |
| 배포한 사이트에서 로그인 403 | 허용 도메인을 환경변수로 분리 | 원인 확인부터 복구까지 **약 1시간** |

<br>

## 담당 역할

서비스를 띄우는 환경부터 테스트, 배포, 모니터링까지 이어지는 인프라 흐름을 맡았습니다.

| 영역 | 한 일 | 코드 |
| --- | --- | --- |
| 실행 환경 | Docker Compose를 데이터, 플랫폼, 백엔드, AI, 모니터링 5개 파일로 나누고 헬스체크와 기동 순서 구성 | [compose.dev.yml](compose.dev.yml), [infra/compose](infra/compose) |
| CI/CD | 바뀐 서비스만 빌드하는 Job 분리, MR 검증, develop 자동 배포, JAR 재사용, Gradle 캐시 | [.gitlab-ci.yml](.gitlab-ci.yml), [.gitlab/ci](.gitlab/ci) |
| 서버 분리 | 개발 서버에서 빌드한 이미지와 화면 파일을 운영 서버로 옮기는 배포 절차 구성 | [트러블슈팅 3](#3-빌드가-몰리면-cpu가-95까지-오르는-문제) |
| 접근 제어 | 서비스별 PostgreSQL 계정, Redis ACL, Nginx HTTPS, 환경변수 주입 | [DB 초기화](infra/postgres/init/01-create-service-databases.sh), [Redis ACL](infra/redis/start-redis.sh) |
| 모니터링 | Prometheus, Grafana, node-exporter 연동과 CPU, RPS 대시보드 | [prometheus.yml](infra/monitoring/prometheus.yml), [대시보드](infra/monitoring/grafana/dashboards/server-cpu.json) |

<br>

## 트러블슈팅

### 1. 배포할 때마다 같은 코드를 다시 컴파일하는 문제

**문제**
배포가 서비스마다 1~3분씩 걸렸습니다. 서비스가 7개라 여러 서비스가 함께 반영되면 기다리는 시간이 길어졌습니다.

**원인**
테스트 단계에서 이미 실행 파일(JAR)을 만들었는데 배포용 Dockerfile 안에서 Gradle 빌드를 한 번 더 하고 있었습니다.

**해결**
```text
변경 전  CI에서 Gradle 빌드 → 배포 Dockerfile에서 Gradle 빌드 다시 실행 → 컨테이너 교체
변경 후  CI에서 Gradle 빌드 → JAR를 아티팩트로 전달 → JAR만 복사해 이미지 생성 → 컨테이너 교체
```
- CI Job이 `build/libs/*.jar`를 아티팩트로 남기고 배포 Job은 `needs`로 받아 씁니다.
- 실행용 JAR가 정확히 하나인지 확인한 뒤 [JAR 전용 Dockerfile](infra/docker/Dockerfile.ci)로 이미지를 만듭니다.
- Compose는 `--no-deps --no-build --wait`로 해당 서비스만 바꿉니다.

**결과** (커밋 `b6b0b34` 적용 전후, 개발 서버 Runner 로그의 배포 Job 평균)

| 서비스 | 적용 전 | 적용 후 |
| --- | ---: | ---: |
| Approval | 167초 | 26초 |
| Eureka | 127초 | 14초 |
| Gateway | 124초 | 23초 |
| User | 110초 | 30초 |
| Attendance | 94초 | 32초 |
| Leave | 86초 | 26초 |
| Config | 59초 | 11초 |

서비스 7개의 배포 시간이 **평균 1~3분에서 30초 안팎**으로 줄었습니다.

[배포 Job](.gitlab/ci/deploy.yml) | [JAR 전용 Dockerfile](infra/docker/Dockerfile.ci)

---

### 2. CI가 돌 때마다 Gradle 의존성을 새로 받는 문제

**문제**
CI는 매번 새 컨테이너에서 실행되기 때문에 Gradle Wrapper와 라이브러리를 매번 처음부터 내려받았습니다.

**해결**
`GRADLE_USER_HOME`을 프로젝트 안의 `.gradle-ci`로 지정하고 Job 이름별로 캐시를 나눴습니다. 캐시에는 Wrapper와 의존성만 넣고 빌드 결과물은 넣지 않았습니다.

```yaml
variables:
  GRADLE_USER_HOME: "$CI_PROJECT_DIR/.gradle-ci"
cache:
  key: "gradle-v1-$CI_JOB_NAME"
  paths:
    - .gradle-ci/wrapper/
    - .gradle-ci/caches/modules-2/
```

**결과**

| 비교 | 캐시 없음 | 캐시 적중 |
| --- | ---: | ---: |
| 같은 커밋(`cf2ff6b`)의 User Gradle 빌드 시간 | 2분 38초 | 1분 35초 |
| User 빌드 Job 전체 시간 (적용 전후) | 약 3분 (183초) | 1분 50초 안팎 (108~116초) |

같은 커밋에서 Gradle 빌드 시간이 **63초, 약 40% 줄었습니다.**

[캐시 설정](.gitlab/ci/gradle.yml)

---

### 3. 빌드가 몰리면 CPU가 95%까지 오르는 문제

**문제**
처음에는 서버 한 대에서 서비스 실행과 CI 빌드를 함께 했습니다. 평소 15% 안팎이던 CPU 사용률이 빌드가 몰리면 **10분 평균 최대 95%** 까지 올랐습니다.

| 시점 (KST) | 10분 평균 CPU | 상황 |
| --- | ---: | --- |
| 11월 6일 14:00 | **95.1%** | 빌드 Job 약 20개가 2개씩 동시에 실행 |
| 11월 6~14일 중 다른 최고값 | 94.1%, 88.5%, 85.7% | 빌드가 몰린 시간대 |
| 같은 기간 하루 평균 | 13.6~26.9% | 평소 |

**판단**
이 상태에서 실제 사용자 요청까지 들어오면 응답이 늦어지거나 서버가 멈출 수 있다고 봤습니다.

**해결** : 개발 서버와 운영 서버 분리
- **개발 서버 (EC2)** : 개발용 서비스, GitLab Runner, 테스트와 빌드, Prometheus와 Grafana
- **운영 서버 (Lightsail)** : Nginx, 업무 서비스, FastAPI, PostgreSQL, Redis, Kafka만 실행
- master 커밋은 개발 서버에서 이미지와 화면 파일을 만들고 SHA-256으로 검증한 뒤 운영 서버로 옮겨 실행합니다.

**결과**
- 운영 서버 컨테이너 13개가 모두 healthy이고 Runner, Prometheus, Grafana는 **하나도 없습니다.** (개발 서버는 18개)
- 운영 릴리스 `1eea33a`에서 바꾼 컨테이너 8개가 healthy였고 배포한 `index.html`과 실제 HTTPS 응답의 SHA-256이 같은 것을 확인했습니다.

> 운영 서버의 응답 속도를 분리 전후로 비교한 부하 테스트는 하지 않았습니다. 그래서 응답 속도 개선율은 성과로 적지 않았습니다.

[상세 기록](DOCS/infra-troubleshooting.md) | [대시보드 설정](infra/monitoring/grafana/dashboards/server-cpu.json)

---

### 4. 모든 서비스가 관리자 DB 계정 하나를 쓰는 문제

**문제**
처음 만든 PostgreSQL에는 관리자 계정 하나만 있었습니다. 근태 서비스에 문제가 생기면 급여나 회원 데이터까지 건드릴 수 있는 구조였습니다.

**해결**
DB가 처음 뜰 때 실행되는 [초기화 스크립트](infra/postgres/init/01-create-service-databases.sh)에서 서비스 7개의 DB와 계정을 따로 만들었습니다.

```sql
CREATE ROLE nexa_leave_app LOGIN PASSWORD ...;
CREATE DATABASE nexa_leave OWNER nexa_leave_app;
REVOKE ALL ON DATABASE nexa_leave FROM PUBLIC;   -- 다른 계정의 접속 차단
GRANT CONNECT, TEMPORARY ON DATABASE nexa_leave TO nexa_leave_app;
```
Redis도 같은 방식으로 서비스별 ACL 계정을 나눴습니다. ([start-redis.sh](infra/redis/start-redis.sh))

**트레이드오프**
새 서비스가 생길 때마다 계정을 추가해야 하지만 한 서비스의 문제가 다른 서비스 데이터로 번지는 범위는 줄였습니다. PostgreSQL 인스턴스 자체는 하나라서 DB 서버 장애까지 나눈 구조는 아닙니다.

---

### 5. 배포한 사이트에서 로그인만 403이 나는 문제

**문제**
운영 도메인에서 로그인 요청(`POST /api/v1/auth/login`)이 계속 403으로 실패했습니다. 서버 안에서 직접 보내면 400이 나와 API 자체는 살아 있었습니다.

**원인**
Gateway의 CORS 허용 도메인이 코드에 `http://localhost:5173` 하나로 고정돼 있어 운영 도메인에서 온 요청을 막고 있었습니다.

**해결**
허용 도메인을 `CORS_ALLOWED_ORIGINS` 환경변수로 빼서 서버마다 다른 값을 넣을 수 있게 했습니다. (커밋 `12acc8d`)

**결과** (Nginx 접속 로그, 11월 5일)

| 시각 (KST) | 기록 |
| --- | --- |
| 09:30 ~ 09:55 | 로그인 요청 403 6건 |
| 09:59 | CORS 환경변수 커밋 |
| 10:30 | 첫 로그인 성공 (200) |

---

### 그 밖에 해결한 운영 문제

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| `.env`를 고쳤는데 AI 서비스가 예전 키를 사용 | `restart`는 컨테이너를 만들 때의 환경변수를 그대로 씀 | 컨테이너를 다시 생성(recreate)하고 새 값이 들어갔는지 확인 |
| 한 서비스 배포가 다른 서비스의 오래된 배포 때문에 막힘 | 서비스들이 같은 GitLab environment 이름을 씀 | `development/서비스명`으로 분리 |
| 빌드 산출물이 쌓여 저장 공간 사용이 늘어남 | 아티팩트 보관 기간이 김 | 보관 기간을 2일로 줄임 |

<br>

## 측정 방법

위 숫자는 개발 서버에 **읽기 전용으로 접속해 기록을 다시 확인한 값**입니다.

| 항목 | 확인한 기록 | 방법 |
| --- | --- | --- |
| 배포 시간 | GitLab Runner Job 로그 | 커밋 `b6b0b34` (10월 28일 10:05) 전후로 서비스별 배포 Job 시간을 모아 평균 |
| Gradle 캐시 | Runner 로그의 `BUILD SUCCESSFUL in ...` | 같은 커밋 `cf2ff6b`의 첫 실행과 캐시 적중 실행 비교, User 빌드 Job은 적용 전후 비교 |
| CPU 사용률 | `sar` (10분 평균, 11월 6~14일) | 하루 평균과 최고값을 뽑고 최고값 시각의 Runner Job 수와 대조 |
| 로그인 403 | Nginx access log | `/api/v1/auth/login` 요청의 상태 코드와 시각을 커밋 시각과 대조 |
| 서버 분리 | 두 서버의 `docker ps` | 운영 서버 13개와 개발 서버 18개 컨테이너 목록 비교 |

짧은 기간의 실제 기록이라 반복 실험한 평균은 아닙니다. 부하 테스트로 잰 응답 속도와 동시 사용자 수는 포함하지 않았습니다.

<br>

## 주요 기능

| 영역 | 할 수 있는 일 |
| --- | --- |
| 인사와 조직 | 사원과 조직 정보 관리, 부서와 직급 조회 |
| 근태와 휴가 | 출퇴근 기록, 휴가와 초과근무 신청을 전자결재와 연결 |
| 전자결재 | 기안, 결재선 구성, 승인과 반려, 결과를 관련 서비스로 전달 |
| 급여 | 근태와 기준 정보로 급여 계산, 명세서 조회 |
| 데이터 이관 | 엑셀과 CSV 업로드, 컬럼 매핑, 검증, 미리보기, 적재 |
| AI 업무 자동화 | OCR, 정보 추출, 규정 검토, 사람 확인, 결재, 엑셀 생성 노드를 연결해 반복 업무 실행 |

| AI 업무 자동화 빌더 | 워크플로 실행 결과 |
| :---: | :---: |
| ![워크플로 빌더](DOCS/readme/automation-builder.jpg) | ![워크플로 실행 결과](DOCS/readme/automation-run-result.jpg) |
| **데이터 이관과 컬럼 매핑** | **자동으로 올라간 결재 문서** |
| ![데이터 이관 매핑](DOCS/readme/migration-mapping.jpg) | ![자동 상신 결재 문서](DOCS/readme/automation-approval.jpg) |

기능별 자세한 설명은 [프로젝트 소개](DOCS/project-overview.md)에 있습니다.

<br>

## 아키텍처

![NEXA 개발 환경의 서비스 구성도](DOCS/readme/architecture.png)

위 그림은 개발 환경의 서비스 연결 구조입니다. 업무 서비스마다 컨테이너와 DB 계정을 나눴고 PostgreSQL 인스턴스는 하나를 같이 씁니다.

```mermaid
flowchart LR
    GitLab["GitLab<br>소스 / MR"] --> DevCI
    subgraph Dev["개발 서버 (AWS EC2)"]
        DevCI["GitLab Runner<br>테스트 / 빌드"]
        DevApp["개발 서비스<br>Docker Compose"]
        Metrics["Prometheus / Grafana"]
        DevCI -->|"develop 자동 배포"| DevApp
        Metrics -.->|"메트릭 수집"| DevApp
    end
    DevCI -->|"master 릴리스 빌드"| Release["이미지 / 화면 파일<br>SHA-256 검증"]
    Release -->|"전달 후 배포"| ProdApp
    subgraph Prod["운영 서버 (Amazon Lightsail)"]
        Nginx["Nginx<br>HTTPS / Frontend"] --> Gateway["API Gateway"]
        Gateway --> ProdApp["업무 서비스 / FastAPI"]
        ProdApp --> Data["PostgreSQL / Redis / Kafka"]
    end
    Browser["사용자"] --> Nginx
```

<br>

## CI/CD 흐름

| 시점 | 하는 일 |
| --- | --- |
| MR 생성 | `rules:changes`로 바뀐 서비스만 테스트와 빌드. Java 서비스는 Testcontainers로 DB를 띄워 JUnit 실행 |
| develop 반영 | 바뀐 서비스의 JAR를 만들어 개발 서버에 자동 배포 |
| 개발 배포 | JAR로 이미지 생성 → `compose up --no-deps --no-build --wait` → 헬스체크. `resource_group`으로 배포가 겹치지 않게 순서대로 실행 |
| master 반영 | 개발 서버에서 빌드 → 이미지와 화면 파일 압축 → 체크섬 검증 → 운영 서버에서 불러와 교체 → 헬스체크와 화면 해시 확인 |

운영 배포는 GitLab CI와 수동 배포 절차를 함께 썼습니다. 이 GitHub 저장소에는 GitHub Actions를 따로 추가하지 않았습니다.

<br>

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Frontend | React 18, TypeScript, Vite, TanStack Query, Zustand, React Flow |
| Backend | Java 21, Spring Boot 4, Spring Cloud Gateway, Eureka, Config, JPA, Flyway |
| AI | Python, FastAPI, LangGraph, DSPy, vLLM, PaddleOCR, MLflow |
| Data | PostgreSQL 17, Redis 7, Kafka 3.9 |
| Infra | AWS EC2, Amazon Lightsail, Docker, Docker Compose, Nginx, Certbot, Tailscale |
| CI / 모니터링 | GitLab CI/CD, Gradle, Testcontainers, Prometheus, Grafana, node-exporter, kafka-exporter |

<br>

## 한계와 다음 과제

- PostgreSQL과 Gateway가 각각 하나라서 이 둘에 장애가 나면 전체 서비스가 영향을 받습니다.
- 운영 배포는 컨테이너를 하나씩 교체하는 방식이라 교체하는 순간 짧게 끊길 수 있습니다.
- 다음 단계는 운영 서버 부하 테스트, Gateway 다중화, DB 분리와 이중화입니다.

<br>

## 폴더 구조

```text
Backend/            업무 서비스, Gateway, Eureka, Config, 공통 인증 라이브러리
FastAPI/            AI 워크플로, GPU 연동, MLOps
Frontend/           ERP 화면과 업무 자동화 빌더
.gitlab/ci/         서비스별 CI/CD Job
infra/compose/      데이터, 플랫폼, 백엔드, AI, 모니터링 Compose
infra/docker/       CI가 만든 JAR로 이미지를 만드는 Dockerfile
infra/postgres/     서비스별 DB와 계정 초기화
infra/redis/        서비스별 Redis ACL
infra/monitoring/   Prometheus 설정과 Grafana 대시보드
DOCS/               기능 설명, 이벤트 계약, QA, 인프라 트러블슈팅
```

실행은 [`.env.example`](.env.example)과 [`compose.dev.yml`](compose.dev.yml)에서 시작합니다. 전체를 띄우려면 별도 Config 저장소, 서비스별 비밀번호, 공통 라이브러리가 있는 GitLab Maven 저장소 접근 권한이 필요합니다. 공개본에는 실제 환경변수와 인증 키를 넣지 않았습니다.

<br>

## 팀 구성

| 이름 | 역할 |
| --- | --- |
| 황문규 | Leader / Backend |
| 박성현 | Backend |
| 황우찬 | Backend |
| 김동현 | Backend / AI |
| 김승환 | Backend / Data |
| **강석진** | **Infrastructure / CI/CD** |

원본은 SSAFY GitLab `S15P31D107` 프로젝트이고 공개본은 `develop` 브랜치의 `701c687` 커밋 기준입니다.
