package com.ssafy.leaveservice.global.config;

import com.ssafy.leaveservice.client.hr.HrClient;
import com.ssafy.leaveservice.client.hr.HrIntegrationProperties;
import com.ssafy.leaveservice.client.hr.UnavailableHrClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HrIntegrationProperties.class)
public class LeaveHrConfiguration {
    @Bean
    @ConditionalOnMissingBean(HrClient.class)
    public HrClient unavailableHrClient() {
        return new UnavailableHrClient();
    }
}
