# NEXA 인프라·CI/CD 개선 기록

담당자 강석진 · 6인 / 8주 팀 프로젝트

프로젝트 중 확인한 병목과 적용한 변경을 정리했습니다. 기능 개발 전체의 성과와 개인의 인프라 개선을 구분하고, 시간 비교는 측정한 작업 범위에 한정했습니다.

## 1. 배포 단계의 중복 Java 빌드 제거

CI Job이 실행용 JAR를 만들었지만 배포 Docker 빌드에서도 Java 코드를 다시 컴파일했습니다. 앞 단계의 산출물이 활용되지 않아 작업이 중복됐습니다.

1. 서비스별 CI에서 `build/libs/*.jar`를 아티팩트로 보관합니다.
2. 배포 Job의 `needs`로 해당 빌드 산출물을 받습니다.
3. `*-plain.jar`를 제외하고 실행용 JAR가 정확히 하나인지 확인합니다.
4. 임시 빌드 컨텍스트에 `app.jar`를 넣고 산출물 전용 Dockerfile로 이미지를 만듭니다.
5. Compose는 `--no-deps --no-build --wait`로 대상 서비스를 갱신합니다.

```text
변경 전  CI Gradle 빌드 → 배포 Docker 빌드에서 Gradle 재실행 → 컨테이너 갱신
변경 후  CI Gradle 빌드 → JAR 아티팩트 전달 → 실행 이미지 구성 → 컨테이너 갱신
```

**배포 단계의 Java 재컴파일을 제거하고 CI가 만든 JAR를 배포 이미지에 사용했습니다.**

MR과 develop 파이프라인 사이에서 JAR를 재사용하는 방식은 아닙니다. 각 파이프라인의 빌드·배포 Job 사이에서 산출물을 전달합니다. develop 반영 시에는 중복 테스트를 생략하지만 병합된 코드의 JAR는 다시 만듭니다.

[배포 Job](../.gitlab/ci/deploy.yml) · [User CI](../.gitlab/ci/user.yml) · [Dockerfile](../infra/docker/Dockerfile.ci) · [User Dockerfile](../infra/docker/Dockerfile.user-ci)

원본 GitLab 변경 커밋 `b6b0b34`.

## 2. Gradle 캐시로 반복 빌드 시간 단축

컨테이너 기반 CI가 반복 실행될 때 Wrapper와 의존성 다운로드 비용이 발생했습니다. `.gradle-ci`를 Gradle 사용자 디렉터리로 지정하고 Job 이름별로 캐시를 분리했습니다.

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

[실제 캐시 템플릿](../.gitlab/ci/gradle.yml). 캐시 대상은 Wrapper와 의존성이며 이전 실행의 JAR를 재활용하는 설정은 아닙니다.

| 항목 | 첫 실행 | 캐시 적중 실행 |
| --- | --- | --- |
| 서비스 | User | User |
| MR / 커밋 | MR !84 / `cf2ff6be` | MR !84 / `cf2ff6be` |
| GitLab Job | `733490` | `734362` |
| Gradle 로그 | `BUILD SUCCESSFUL in 2m 38s` | `BUILD SUCCESSFUL in 1m 35s` |
| 시간 | 158초 | 95초 |

**63초, 약 40% 단축**했습니다. 계산은 `(158 - 95) / 158 × 100`입니다.

같은 커밋의 두 실행에서 관측한 Gradle 작업 시간입니다. 대기·업로드·배포를 포함하는 전체 파이프라인 지표나 반복 측정한 평균이 아닙니다. 원본 로그에는 Job 토큰 등 운영 정보가 있어 파일 전체 대신 식별자와 시간만 기록했습니다.

## 3. CI 부하와 운영 요청의 자원 경쟁 분리

개발 서버에서 업무 서비스와 CI를 함께 실행했습니다. Grafana의 서버 전체 CPU 패널에서 평소 약 15% 수준이던 사용률이 CI 실행 구간에 약 84%까지 상승했습니다. 특정 서비스 하나의 빌드에 한정된 현상으로 해석하지 않았습니다.

```promql
100 * (
  1 - avg by (instance) (
    rate(node_cpu_seconds_total{job="node-exporter",mode="idle"}[1m])
  )
)
```

패널은 1분 구간의 평균 CPU 사용률을 계산합니다. 84% 자체가 장애라는 의미는 아니지만 빌드와 요청 처리를 함께 수행할 때 CPU 여유가 줄어드는 위험을 확인했습니다.

| 환경 | 역할 |
| --- | --- |
| 개발 EC2 | 개발 서비스, GitLab Runner, 테스트·빌드, Prometheus·Grafana |
| 운영 Lightsail | Nginx, 업무 서비스·FastAPI, PostgreSQL·Redis·Kafka |
| 운영 배포 | master 커밋의 이미지·프론트를 개발 서버에서 빌드하고 산출물을 전달해 실행 |

운영 서버에 Runner를 설치하지 않고 개발용 모니터링 스택도 운영 배포 대상에서 제외했습니다. 운영 상태는 헬스체크와 로그·API 응답으로 확인했습니다. 운영 지표의 지속 수집은 추가 개선 과제입니다.

### 배포 검증

`1eea33a25e2e385c36e3e86ce851ed3841fce756` 릴리스에서 다음을 확인했습니다.

- 이미지 revision과 실행 컨테이너 라벨로 배포 버전 확인
- User·Approval·Attendance·Leave·Migration·Payroll·Gateway·FastAPI 컨테이너 8개의 `healthy` 상태
- 배포 아카이브의 프론트 `index.html`과 실제 HTTPS 응답의 SHA-256 일치

**결과는 CI와 운영 서비스의 실행 자원을 분리한 것입니다.** 같은 조건에서 운영 지연·처리량을 비교한 부하 테스트는 수행하지 않았으므로 응답 속도 개선율·동시 사용자 수를 성과로 주장하지 않습니다.

[CPU·RPS 대시보드](../infra/monitoring/grafana/dashboards/server-cpu.json) · [Prometheus 설정](../infra/monitoring/prometheus.yml)

## 함께 해결한 운영 문제

| 증상 | 확인한 원인 | 대응 |
| --- | --- | --- |
| `.env` 변경 후 AI 서비스가 예전 키 사용 | restart는 컨테이너 생성 당시 환경변수를 재사용 | Compose가 읽는 값의 길이를 확인하고 대상 컨테이너를 recreate한 뒤 새 값 주입 확인 |
| 외부 도메인 요청은 403, 내부 직접 요청은 400 | 허용 Origin과 운영 도메인 불일치 | CORS 설정 반영 후 Origin을 포함한 요청으로 재검증 |
| 서비스 배포가 오래된 배포로 차단 | 서로 다른 서비스가 같은 GitLab environment 이름 사용 | `development/$DEPLOY_SERVICE`, `development/fastapi`, `development/frontend`로 분리 |
| 아티팩트 누적에 따른 저장 공간 사용 증가 | 산출물 보관 기간 | 보관 기간을 2일로 조정 |

이 항목들은 운영 복구와 배포 신뢰성을 위한 작업이며 성능 향상을 직접 측정한 사례와 구분했습니다.

## 적용 범위

컨테이너·DB·계정 분리는 적용했지만 DB 인스턴스 다중화나 Gateway 이중화까지 구현한 것은 아닙니다. 운영 수동 배포 스크립트와 실제 비밀값·인증 키는 공개본에 포함하지 않았습니다. 저장소에서 확인 가능한 CI·Compose·대시보드 설정과 운영 당시 검증한 절차를 구분했습니다.

[README로 돌아가기](../README.md)
