package com.ssafy.approval_service.domain.approval;

import com.jayway.jsonpath.JsonPath;
import com.ssafy.approval_service.global.outbox.KafkaTopicConfig;
import com.ssafy.approval_service.global.outbox.OutboxEvent;
import com.ssafy.approval_service.global.outbox.OutboxEventRepository;
import com.ssafy.approval_service.global.outbox.OutboxPublisher;
import com.ssafy.approval_service.support.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ssafy.approval_service.support.ApprovalTestFixtures.BASE;
import static com.ssafy.approval_service.support.ApprovalTestFixtures.line;
import static com.ssafy.approval_service.support.ApprovalTestFixtures.path;
import static com.ssafy.approval_service.support.ApprovalTestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결재가 최종 처리되면 결과 이벤트가 아웃박스에 기록되고, 발행기가 Kafka 로 보낸다.
 * 다른 테스트와 아웃박스·토픽을 공유하므로 이 테스트가 만든 문서의 이벤트만 골라서 확인한다.
 */
class ApprovalResultEventIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    OutboxPublisher outboxPublisher;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(KafkaTopicConfig.APPROVAL_RESULT_TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    /** 휴가 신청에서 온 문서를 만든다 */
    private long createLeaveDocument(long drafter, long approver, long sourceId) throws Exception {
        String body = mvc.perform(post(BASE).with(user(drafter)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"휴가 신청","formType":"LEAVE","sourceType":"LEAVE","sourceId":%d,
                         "requestKey":"%s","approvalLines":[%s]}"""
                        .formatted(sourceId, UUID.randomUUID(), line(approver, 1, "APPROVER"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.data.documentId");
        return id.longValue();
    }

    private List<OutboxEvent> outboxOf(long documentId) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> e.getMessageKey().equals(String.valueOf(documentId)))
                .toList();
    }

    /** 이 문서의 메시지가 올 때까지 토픽을 읽는다 */
    private ConsumerRecord<String, String> consumeOf(long documentId) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 15_000;
        while (found.isEmpty() && System.currentTimeMillis() < deadline) {
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1)).forEach(record -> {
                if (record.key().equals(String.valueOf(documentId))) {
                    found.add(record);
                }
            });
        }
        assertThat(found).as("문서 %d 의 결과 메시지", documentId).hasSize(1);
        return found.getFirst();
    }

    @Test
    void 최종_승인되면_아웃박스에_기록되고_발행하면_Kafka로_간다() throws Exception {
        long documentId = createLeaveDocument(9401L, 9402L, 601L);
        // 결재 진행 중에는 이벤트 없음
        assertThat(outboxOf(documentId)).isEmpty();

        mvc.perform(post(path(documentId, "/approve")).with(user(9402L))).andExpect(status().isOk());

        // 같은 트랜잭션에서 아웃박스에 1건 기록, 아직 발행 전
        List<OutboxEvent> recorded = outboxOf(documentId);
        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().isPublished()).isFalse();

        outboxPublisher.publishPending();

        assertThat(outboxEventRepository.findById(recorded.getFirst().getId()).orElseThrow().isPublished()).isTrue();
        ConsumerRecord<String, String> record = consumeOf(documentId);
        String payload = record.value();
        assertThat((String) JsonPath.read(payload, "$.status")).isEqualTo("APPROVED");
        assertThat((String) JsonPath.read(payload, "$.sourceType")).isEqualTo("LEAVE");
        assertThat(((Number) JsonPath.read(payload, "$.sourceId")).longValue()).isEqualTo(601L);
        assertThat((String) JsonPath.read(payload, "$.eventId")).isEqualTo(recorded.getFirst().getEventId().toString());
        assertThat((String) JsonPath.read(payload, "$.eventType")).isEqualTo("DOCUMENT_COMPLETED");
    }

    @Test
    void 반려와_회수도_결과_이벤트를_남긴다() throws Exception {
        long rejected = createLeaveDocument(9403L, 9404L, 602L);
        mvc.perform(post(path(rejected, "/reject")).with(user(9404L))
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"일정 겹침\"}"))
                .andExpect(status().isOk());

        long cancelled = createLeaveDocument(9405L, 9406L, 603L);
        mvc.perform(post(path(cancelled, "/cancel")).with(user(9405L))).andExpect(status().isOk());

        assertThat((String) JsonPath.read(outboxOf(rejected).getFirst().getPayload(), "$.status")).isEqualTo("REJECTED");
        assertThat((String) JsonPath.read(outboxOf(cancelled).getFirst().getPayload(), "$.status")).isEqualTo("CANCEL");
    }

    @Test
    void 중간_결재와_직접_기안_문서는_이벤트가_없다() throws Exception {
        // 결재자 2명 중 1명만 승인 → 아직 진행 중
        String body = mvc.perform(post(BASE).with(user(9407L)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"휴가 신청","formType":"LEAVE","sourceType":"LEAVE","sourceId":604,
                         "requestKey":"%s","approvalLines":[%s,%s]}"""
                        .formatted(UUID.randomUUID(), line(9408L, 1, "APPROVER"), line(9409L, 2, "APPROVER"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long inProgress = ((Number) JsonPath.read(body, "$.data.documentId")).longValue();
        mvc.perform(post(path(inProgress, "/approve")).with(user(9408L))).andExpect(status().isOk());

        // 직접 기안 문서는 최종 승인돼도 결과를 받을 원본 서비스가 없다
        long direct = createDocument(9410L, line(9411L, 1, "APPROVER"));
        mvc.perform(post(path(direct, "/approve")).with(user(9411L))).andExpect(status().isOk());

        assertThat(outboxOf(inProgress)).isEmpty();
        assertThat(outboxOf(direct)).isEmpty();
    }

    @Test
    void 결재_처리가_실패해_롤백되면_이벤트도_남지_않는다() throws Exception {
        long documentId = createLeaveDocument(9412L, 9413L, 605L);

        // 차례가 아닌 사원의 승인 → 403, 트랜잭션 롤백
        mvc.perform(post(path(documentId, "/approve")).with(user(9499L))).andExpect(status().isForbidden());

        assertThat(outboxOf(documentId)).isEmpty();
    }
}
