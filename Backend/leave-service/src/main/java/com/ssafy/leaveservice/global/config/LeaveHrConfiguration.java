package com.ssafy.leaveservice.global.config;

import com.ssafy.leaveservice.client.hr.HrClient;
import com.ssafy.leaveservice.client.hr.HrIntegrationProperties;
import com.ssafy.leaveservice.client.hr.UnavailableHrClient;
import com.ssafy.leaveservice.client.hr.UserServiceHrClient;
import com.ssafy.nexa.security.BearerTokenRelayInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HrIntegrationProperties.class)
public class LeaveHrConfiguration {
    @Bean
    @ConditionalOnMissingBean(HrClient.class)
    public HrClient hrClient(
            HrIntegrationProperties properties,
            ObjectProvider<BearerTokenRelayInterceptor> bearerTokenRelay
    ) {
        if (!properties.enabled() || properties.baseUrl() == null || properties.baseUrl().isBlank()
                || properties.employeeInfoUri() == null || properties.employeeInfoUri().isBlank()) {
            return new UnavailableHrClient();
        }

        // 명시한 User Service 내부 주소에만 현재 사용자의 토큰을 전달한다.
        URI baseUri = URI.create(properties.baseUrl());
        if (!List.of("http", "https").contains(baseUri.getScheme()) || baseUri.getHost() == null
                || baseUri.getRawUserInfo() != null || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("HR requires a valid internal User Service URL");
        }
        for (String endpoint : new String[] { properties.employeeInfoUri(), properties.departmentMembersUri(), properties.dutyInfoUri() }) {
            if (endpoint == null || endpoint.isBlank()) { continue; }
            if (!endpoint.startsWith("/") || endpoint.startsWith("//") || endpoint.contains("://")) {
                throw new IllegalArgumentException("HR endpoints must be relative API paths");
            }
        }
        HttpClient transport = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(transport);
        requests.setReadTimeout(properties.readTimeout());
        RestClient client = RestClient.builder().baseUrl(baseUri.toString()).requestFactory(requests)
                .requestInterceptor(bearerTokenRelay.getObject()).build();
        return new UserServiceHrClient(client, properties);
    }
}
