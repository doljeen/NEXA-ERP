# NEXA 인프라 트러블슈팅 상세 기록

강석진 | Infrastructure / CI/CD | 6명 8주 팀 프로젝트

프로젝트 중 발견한 문제와 바꾼 내용, 그리고 결과를 어떻게 확인했는지 정리했습니다.
수치는 모두 개발 서버에 읽기 전용으로 접속해 Runner 로그, `sar`, Nginx 접속 로그, `docker ps`로 다시 확인했습니다.

| 번호 | 문제 | 결과 |
| --- | --- | --- |
| 1 | 배포할 때 Java 코드를 다시 컴파일 | 서비스 7개 배포 평균 1~3분 → 30초 안팎 |
| 2 | CI마다 Gradle 의존성을 새로 다운로드 | 같은 커밋 Gradle 빌드 2분 38초 → 1분 35초 |
| 3 | 빌드가 몰리면 CPU 10분 평균 95% | 개발 서버와 운영 서버 분리 |
| 4 | 관리자 DB 계정 하나를 모든 서비스가 공유 | 서비스별 DB와 계정 분리 |
| 5 | 운영 도메인에서 로그인 403 | 약 1시간 만에 복구 |

---

## 1. 배포할 때 Java 코드를 다시 컴파일하는 문제

### 문제
배포가 서비스마다 1~3분씩 걸렸습니다.

### 원인
CI Job이 이미 실행용 JAR를 만들었는데 배포용 Docker 빌드 안에서 Gradle을 한 번 더 실행하고 있었습니다. 앞 단계의 결과물을 버리고 같은 일을 다시 하고 있었습니다.

### 해결
1. 서비스별 CI에서 `build/libs/*.jar`를 아티팩트로 남깁니다.
2. 배포 Job은 `needs`로 그 아티팩트를 받습니다.
3. `*-plain.jar`를 빼고 실행용 JAR가 정확히 하나인지 확인합니다.
4. 임시 폴더에 `app.jar`로 넣고 JAR 전용 Dockerfile로 이미지를 만듭니다.
5. Compose는 `--no-deps --no-build --wait`로 해당 서비스만 바꿉니다.

```text
변경 전  CI Gradle 빌드 → 배포 Docker 빌드에서 Gradle 다시 실행 → 컨테이너 교체
변경 후  CI Gradle 빌드 → JAR 아티팩트 전달 → JAR만 넣어 이미지 생성 → 컨테이너 교체
```

develop에 반영할 때는 테스트를 다시 돌리지 않지만 병합된 코드로 JAR는 새로 만듭니다. MR 파이프라인의 JAR를 develop에서 재사용하는 방식은 아닙니다.

### 결과
커밋 `b6b0b34` (2026-10-28 10:05 KST) 전후 Runner 로그의 배포 Job 평균 시간입니다.

| 서비스 | 적용 전 | 적용 후 |
| --- | ---: | ---: |
| Approval | 167초 | 26초 |
| Eureka | 127초 | 14초 |
| Gateway | 124초 | 23초 |
| User | 110초 | 30초 |
| Attendance | 94초 | 32초 |
| Leave | 86초 | 26초 |
| Config | 59초 | 11초 |

적용 직후 같은 Approval 배포 Job 한 건을 보면 00:46 UTC에 166.6초였고 01:35 UTC에 43.9초였습니다.

[배포 Job](../.gitlab/ci/deploy.yml) | [User CI](../.gitlab/ci/user.yml) | [Dockerfile](../infra/docker/Dockerfile.ci) | [User Dockerfile](../infra/docker/Dockerfile.user-ci)

---

## 2. CI마다 Gradle 의존성을 새로 받는 문제

### 문제
CI는 매번 새 컨테이너에서 돌기 때문에 Gradle Wrapper와 의존성을 매번 처음부터 내려받았습니다.

### 해결
`.gradle-ci`를 Gradle 사용자 폴더로 지정하고 Job 이름별로 캐시를 나눴습니다. 캐시 대상은 Wrapper와 의존성뿐이고 이전 실행의 JAR는 넣지 않았습니다.

```yaml
variables:
  GRADLE_USER_HOME: "$CI_PROJECT_DIR/.gradle-ci"

cache:
  key: "gradle-v1-$CI_JOB_NAME"
  paths:
    - .gradle-ci/wrapper/
    - .gradle-ci/caches/modules-2/
  policy: pull-push
```

### 결과

**같은 커밋 비교**

| 항목 | 첫 실행 | 캐시 적중 실행 |
| --- | --- | --- |
| 서비스 | User | User |
| MR / 커밋 | MR !84 / `cf2ff6be` | MR !84 / `cf2ff6be` |
| GitLab Job | `733490` | `734362` |
| Gradle 로그 | `BUILD SUCCESSFUL in 2m 38s` | `BUILD SUCCESSFUL in 1m 35s` |
| 시간 | 158초 | 95초 |

63초, 약 40% 줄었습니다. (`(158 - 95) / 158 × 100`)

**적용 전후 Job 전체 시간**

커밋 `cf2ff6b` (2026-10-28 10:47 KST) 전후로 User 빌드 Job 전체 시간이 183초에서 108~116초로 줄었습니다.

Gradle 작업 시간과 Job 시간만 비교했습니다. 대기와 업로드까지 포함한 파이프라인 전체 시간은 아닙니다.

[캐시 템플릿](../.gitlab/ci/gradle.yml)

---

## 3. 빌드가 몰리면 CPU가 95%까지 오르는 문제

### 문제
처음에는 서버 한 대에서 업무 서비스와 CI를 함께 실행했습니다. `sar`의 10분 평균 CPU 기록(11월 6~14일)을 보면 다음과 같습니다.

| 구분 | 값 |
| --- | --- |
| 하루 평균 | 13.6~26.9% |
| 최고값 | **95.1%** (11월 6일 14:00 KST) |
| 그 밖의 높은 값 | 94.1%, 88.5%, 85.7% |

95.1%가 나온 시간대에는 `nexa-dev-runner`에서 빌드 Job 약 20개가 2개씩 동시에 돌고 있었습니다. 특정 서비스 하나의 문제가 아니라 빌드가 몰린 탓이었습니다.

Grafana 대시보드는 아래 식으로 서버 전체 CPU를 1분 단위로 보여 줍니다.

```promql
100 * (
  1 - avg by (instance) (
    rate(node_cpu_seconds_total{job="node-exporter",mode="idle"}[1m])
  )
)
```

### 판단
CPU 사용률이 높다고 바로 장애가 나는 것은 아닙니다. 하지만 빌드와 사용자 요청을 같은 서버에서 처리하면 요청에 쓸 CPU 여유가 줄어든다고 판단했습니다.

### 해결

| 환경 | 맡은 일 |
| --- | --- |
| 개발 EC2 | 개발용 서비스, GitLab Runner, 테스트와 빌드, Prometheus와 Grafana |
| 운영 Lightsail | Nginx, 업무 서비스, FastAPI, PostgreSQL, Redis, Kafka |
| 운영 배포 | master 커밋의 이미지와 화면 파일을 개발 서버에서 만들고 운영 서버로 옮겨 실행 |

### 결과
- 운영 서버 `docker ps` : 컨테이너 13개 (gateway, fastapi, payroll, migration, leave, attendance, approval, user, redis, eureka, config, postgres, kafka) 모두 healthy이고 Runner와 모니터링 도구는 없습니다.
- 개발 서버 `docker ps` : 컨테이너 18개 (Runner, Prometheus, Grafana, exporter 포함)

**배포 검증** (릴리스 `1eea33a25e2e385c36e3e86ce851ed3841fce756`)
- 이미지 revision과 실행 중인 컨테이너 라벨로 배포 버전 확인
- 바꾼 컨테이너 8개 (User, Approval, Attendance, Leave, Migration, Payroll, Gateway, FastAPI) 모두 healthy
- 배포 파일의 `index.html`과 실제 HTTPS 응답의 SHA-256 일치

분리 전후로 운영 응답 속도를 비교한 부하 테스트는 하지 않았습니다. 그래서 응답 속도 개선율이나 동시 사용자 수는 성과로 적지 않았습니다. 운영 서버 지표를 계속 수집하는 것은 다음 과제입니다.

[CPU와 RPS 대시보드](../infra/monitoring/grafana/dashboards/server-cpu.json) | [Prometheus 설정](../infra/monitoring/prometheus.yml)

---

## 4. 모든 서비스가 관리자 DB 계정 하나를 쓰는 문제

### 문제
처음 만든 PostgreSQL에는 관리자 계정 하나만 있었습니다. 한 서비스에 문제가 생기면 다른 서비스의 데이터까지 건드릴 수 있었습니다.

### 해결
[초기화 스크립트](../infra/postgres/init/01-create-service-databases.sh)에서 서비스 7개(user, leave, approval, attendance, payroll, migration, ai)마다 DB와 계정을 따로 만들었습니다.

```sql
CREATE ROLE nexa_leave_app LOGIN PASSWORD ...;
CREATE DATABASE nexa_leave OWNER nexa_leave_app;
REVOKE ALL ON DATABASE nexa_leave FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE nexa_leave TO nexa_leave_app;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO nexa_leave_app;
```

비밀번호는 서비스별 환경변수로 넣습니다. Redis도 서비스별 ACL 계정으로 나눴습니다. ([start-redis.sh](../infra/redis/start-redis.sh))

### 트레이드오프
새 서비스가 생길 때마다 계정을 추가해야 합니다. 대신 한 서비스의 문제가 다른 서비스 데이터로 번지는 범위를 줄였습니다. PostgreSQL 인스턴스는 하나라서 DB 서버 장애까지 나눈 것은 아닙니다.

---

## 5. 운영 도메인에서 로그인만 403이 나는 문제

### 문제
운영 도메인에서 `POST /api/v1/auth/login`이 403으로 실패했습니다. 서버 안에서 직접 요청하면 400이 나와 API는 살아 있었습니다.

### 원인
Gateway의 [CORS 설정](../Backend/gateway-service/src/main/java/com/ssafy/gatewayservice/config/CorsConfig.java)이 허용 도메인을 `http://localhost:5173` 하나로 고정하고 있었습니다.

### 해결
허용 도메인을 `CORS_ALLOWED_ORIGINS` 환경변수로 받도록 바꾸고 Compose에서 값을 넣었습니다. 쉼표로 여러 도메인을 넣을 수 있고 값이 없으면 로컬 주소를 씁니다. (커밋 `12acc8d`)

### 결과 (Nginx 접속 로그, 11월 5일 KST)

| 시각 | 기록 |
| --- | --- |
| 09:30 ~ 09:55 | 로그인 요청 403 6건 |
| 09:59 | CORS 환경변수 커밋 |
| 10:30 | 첫 로그인 성공 (200) |

---

## 그 밖에 해결한 운영 문제

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| `.env`를 고쳤는데 AI 서비스가 예전 키를 사용 | `restart`는 컨테이너를 만들 때의 환경변수를 그대로 씀 | Compose가 읽는 값을 확인하고 컨테이너를 recreate해 새 값 주입 확인 |
| 한 서비스 배포가 다른 서비스의 오래된 배포 때문에 막힘 | 여러 서비스가 같은 GitLab environment 이름을 씀 | `development/$DEPLOY_SERVICE`, `development/fastapi`, `development/frontend`로 분리 |
| 빌드 산출물이 쌓여 저장 공간 사용이 늘어남 | 아티팩트 보관 기간이 김 | 보관 기간을 2일로 줄임 |

이 항목들은 배포를 안정적으로 만들기 위한 작업이고 성능을 잰 사례는 아닙니다.

---

## 측정 방법

| 항목 | 확인한 기록 | 방법 |
| --- | --- | --- |
| 배포 시간 | GitLab Runner Job 로그 | `b6b0b34` 전후로 서비스별 배포 Job 시간을 모아 평균 |
| Gradle 캐시 | Runner 로그의 `BUILD SUCCESSFUL in ...` | 같은 커밋의 첫 실행과 캐시 적중 실행 비교, Job 시간은 `cf2ff6b` 전후 비교 |
| CPU | `sar` 10분 평균 (11월 6~14일) | 하루 평균과 최고값을 뽑고 최고값 시각의 Runner Job 수와 대조 |
| 로그인 403 | Nginx access log | 로그인 요청의 상태 코드와 시각을 커밋 시각과 대조 |
| 서버 분리 | 두 서버의 `docker ps` | 컨테이너 목록 비교 |

짧은 기간의 실제 기록이고 같은 조건으로 여러 번 반복한 실험은 아닙니다.

## 적용 범위
- 컨테이너, DB, 계정은 나눴지만 DB 다중화와 Gateway 이중화는 하지 않았습니다.
- 운영 수동 배포 스크립트와 실제 비밀값은 공개본에 넣지 않았습니다.

[README로 돌아가기](../README.md)
