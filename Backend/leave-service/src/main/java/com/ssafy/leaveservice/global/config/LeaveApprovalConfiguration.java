package com.ssafy.leaveservice.global.config;

import com.ssafy.leaveservice.client.approval.ApprovalClient;
import com.ssafy.leaveservice.client.approval.ApprovalHttpClient;
import com.ssafy.nexa.security.BearerTokenRelayInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
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
    public ApprovalClient approvalClient(
            ApprovalProperties properties,
            ObjectProvider<BearerTokenRelayInterceptor> bearerTokenRelay,
            @Value("${leave.approval.http-contract-verified:false}") boolean httpContractVerified,
            @Value("${leave.approval.results.enabled:false}") boolean resultsEnabled,
            @Value("${leave.approval.results.contract-verified:false}") boolean resultsContractVerified,
            @Value("${spring.kafka.bootstrap-servers:}") String kafkaServers
    ) {
        if (!properties.enabled() || !httpContractVerified || !resultsEnabled || !resultsContractVerified
                || kafkaServers.isBlank() || properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            return new UnavailableApprovalClient();
        }

        URI baseUri = URI.create(properties.baseUrl());
        if (!("http".equals(baseUri.getScheme()) || "https".equals(baseUri.getScheme()))
                || baseUri.getHost() == null || baseUri.getRawUserInfo() != null
                || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("Approval requires a valid internal HTTP base URL");
        }
        HttpClient transport = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(transport);
        requests.setReadTimeout(properties.readTimeout());
        RestClient client = RestClient.builder()
                .baseUrl(baseUri.toString())
                .requestFactory(requests)
                .requestInterceptor(bearerTokenRelay.getObject())
                .build();
        return new ApprovalHttpClient(client);
    }
}
