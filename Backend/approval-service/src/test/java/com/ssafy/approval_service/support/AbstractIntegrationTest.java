package com.ssafy.approval_service.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres 컨테이너를 띄우는 통합 테스트 베이스. Docker 가 필요하다.
 * 컨테이너는 JVM 당 한 번만 띄우고 모든 테스트 클래스가 공유한다(싱글턴, user-service 와 같은 방식).
 * Eureka 등록은 끈다. 인증은 각 테스트에서 jwt() 로 흉내 내므로 JWKS 서버도 필요 없다.
 * user-service 는 FakeUserServiceClient 로 대신한다 (지정하지 않은 사원은 모두 재직 중).
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Import(AbstractIntegrationTest.FakeUserServiceConfig.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @TestConfiguration
    static class FakeUserServiceConfig {

        @Bean
        @Primary
        FakeUserServiceClient fakeUserServiceClient() {
            return new FakeUserServiceClient();
        }
    }
}
