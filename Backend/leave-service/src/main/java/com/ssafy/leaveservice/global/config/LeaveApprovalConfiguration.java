package com.ssafy.leaveservice.global.config;

import com.ssafy.leaveservice.client.approval.ApprovalClient;
import com.ssafy.leaveservice.client.approval.ApprovalProperties;
import com.ssafy.leaveservice.client.approval.UnavailableApprovalClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApprovalProperties.class)
public class LeaveApprovalConfiguration {
    @Bean
    @ConditionalOnMissingBean(ApprovalClient.class)
    public ApprovalClient unavailableApprovalClient() {
        // TODO: 실제 제출 의미·멱등성·인증·DTO 계약 확인 후 HTTP 구현체로 교체한다.
        return new UnavailableApprovalClient();
    }
}
