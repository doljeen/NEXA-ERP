package com.ssafy.approval_service.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres 컨테이너를 띄우는 통합 테스트 베이스. Docker 가 필요하다.
 * 컨테이너는 JVM 당 한 번만 띄우고 모든 테스트 클래스가 공유한다(싱글턴, user-service 와 같은 방식).
 * Eureka 등록은 끈다. 인증은 각 테스트에서 jwt() 로 흉내 내므로 JWKS 서버도 필요 없다.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
