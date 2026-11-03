package com.ssafy.attendanceservice.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Kafka 수신 실패 처리 (DOCS/approval-result-event.md).
 * 1초 → 2초 → 4초 재시도 후에도 실패하면 "원래 토픽.DLT"의 같은 파티션으로 옮긴다.
 * 옮기지 않으면 실패한 메시지가 같은 파티션의 뒤 메시지를 모두 막는다.
 */
@Configuration
public class KafkaConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000);
        backOff.setMultiplier(2.0);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
