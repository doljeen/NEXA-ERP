# NEXA 개발환경 기준 — 초안

## 개발 방식

- 각 서비스 담당자가 독립적인 프로젝트를 생성
- Java 서비스는 Temurin JDK 21을 사용
- 각 서비스는 자신의 빌드 설정과 실행 클래스를 가진다.
- 로컬 PostgreSQL은 Docker Compose로 실행한다.
- EC2에서는 Docker Compose로 서비스를 배포한다.

## Java 서비스 공통 버전

- JDK: Eclipse Temurin 21
- Spring Boot: 4.1.1
- Gradle: 9.7.1
- Gradle 설정 문법: Groovy
- 각 서비스의 Gradle Wrapper 버전을 9.7.1로 통일한다.
- IntelliJ의 Project SDK와 Gradle JVM은 모두 Java 21로 설정한다.

## 서비스별 로컬 실행 포트

| 서비스 | 폴더 이름 | 포트 |
|---|---|---|
| Gateway | gateway-service | 8080 |
| 유저 | user-service | 8081 |
| 휴가 | leave-service | 8082 |
| 결재 | approval-service | 8083 |
| 근태 | attendance-service | 8084 |
| 급여 | payroll-service | 8085 |
| AI | ai-service | 8086 |
| Eureka | eureka-server | 8761 |

- 위 포트는 개발 PC에서 서비스를 실행할 때 사용하는 기준이다.
- EC2에서 위 포트를 모두 인터넷에 공개하는 것은 아니다.
- 각 서비스의 실행 포트는 환경변수로 변경할 수 있도록 구성한다.

## 프로젝트 위치

각 서비스 프로젝트는 backend 폴더 아래에 생성한다.

예시:
- backend/user-service
- backend/leave-service
- backend/approval-service

backend 자체는 Spring 프로젝트가 아닌 정리용 폴더다.
서비스 폴더 안에 별도 Git 저장소를 생성하지 않는다.

## 개발용 PostgreSQL

- 각 팀원의 PC에서 Docker Compose로 실행한다.
- PC에서 접속하는 주소: localhost
- PC에서 접속하는 포트: 5433
- PostgreSQL 컨테이너 내부 포트: 5432
- 서비스별 DB와 계정을 분리한다.
- 실제 비밀번호가 들어가는 .env 파일은 Git에 올리지 않는다.
- DB 생성 및 실행 방법은 인프라 설정 후 추가한다.

## 추가 협의 사항

- Gateway와 Eureka의 설정 담당자