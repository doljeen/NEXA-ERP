package com.ssafy.approval_service.global.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

/**
 * 서비스 간 호출 설정. Eureka 서비스 이름(http://user-service)으로 호출하고 로드밸런서가 실제 주소로 바꾼다.
 * 결재선 검증은 트랜잭션 안에서 호출되므로 타임아웃을 짧게 둔다.
 */
@Configuration
public class UserServiceClientConfig {

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    UserServiceClient userServiceClient(@LoadBalanced RestClient.Builder builder,
                                        @Value("${app.user-service.base-url}") String baseUrl,
                                        @Value("${app.user-service.connect-timeout}") Duration connectTimeout,
                                        @Value("${app.user-service.read-timeout}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return create(builder.clone().baseUrl(baseUrl).requestFactory(requestFactory));
    }

    /** 토큰 전달 인터셉터를 붙여 HTTP 인터페이스 구현체를 만든다 (단위 테스트에서도 사용) */
    static UserServiceClient create(RestClient.Builder builder) {
        RestClient restClient = builder.requestInterceptor(bearerTokenRelay()).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(UserServiceClient.class);
    }

    /** 현재 요청을 인증한 access 토큰을 그대로 넘긴다. user-service 가 같은 토큰을 다시 검증한다 */
    static ClientHttpRequestInterceptor bearerTokenRelay() {
        return (request, body, execution) -> {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) {
                request.getHeaders().setBearerAuth(auth.getToken().getTokenValue());
            }
            return execution.execute(request, body);
        };
    }
}
