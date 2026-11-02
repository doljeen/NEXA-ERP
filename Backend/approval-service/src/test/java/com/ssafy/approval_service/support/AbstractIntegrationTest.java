package com.ssafy.approval_service.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static com.ssafy.approval_service.support.ApprovalTestFixtures.BASE;
import static com.ssafy.approval_service.support.ApprovalTestFixtures.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Postgres·Kafka 컨테이너를 띄우는 통합 테스트 베이스. Docker 가 필요하다.
 * 컨테이너는 JVM 당 한 번만 띄우고 모든 테스트 클래스가 공유한다(싱글턴, user-service 와 같은 방식).
 * Eureka 등록은 끈다. 인증은 각 테스트에서 jwt() 로 흉내 내므로 JWKS 서버도 필요 없다.
 * user-service 는 FakeUserServiceClient 로 대신한다 (지정하지 않은 사원은 모두 재직 중).
 * config-server 는 쓰지 않는다 (Kafka 주소는 @ServiceConnection 이 넣어 준다). 아웃박스 스케줄러는 끈다. 발행은 테스트가 OutboxPublisher 를 직접 호출해 확인한다.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.config.enabled=false",
        "app.outbox.scheduler.enabled=false"})
@AutoConfigureMockMvc
@Import(AbstractIntegrationTest.FakeUserServiceConfig.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    /** compose 와 같은 공식 이미지·버전 (KRaft 단일 노드) */
    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @Autowired
    protected MockMvc mvc;

    /** 문서 생성 + 상신 요청. 문서번호는 서버가 만든다 */
    protected ResultActions submit(long drafter, String... lines) throws Exception {
        return mvc.perform(post(BASE).with(user(drafter)).contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"테스트 문서 %d","formType":"GENERAL","approvalLines":[%s]}"""
                .formatted(drafter, String.join(",", lines))));
    }

    /** 상신에 성공한 문서의 ID */
    protected long createDocument(long drafter, String... lines) throws Exception {
        MvcResult created = submit(drafter, lines).andExpect(status().isCreated()).andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.documentId");
        return id.longValue();
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
