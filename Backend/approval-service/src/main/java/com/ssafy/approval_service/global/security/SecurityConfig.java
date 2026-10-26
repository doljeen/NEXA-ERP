package com.ssafy.approval_service.global.security;

import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    private final BearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    private static final String[] PERMIT_URLS = {
            "/actuator/health",
            "/error"
    };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_URLS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(cookieOrHeaderResolver())
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint) // 토큰이 틀렸을 때
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                        .jwt(j -> j.jwtAuthenticationConverter(roleConverter())))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint) // 토큰이 없을 때
                        .accessDeniedHandler(jwtAccessDeniedHandler))          // 토큰은 맞는데 권한이 부족할 때
                .build();
    }

    // 브라우저 요청은 accessToken 쿠키, 서비스 간 호출은 Authorization 헤더
    BearerTokenResolver cookieOrHeaderResolver() {
        return request -> {
          Cookie cookie = WebUtils.getCookie(request, SecurityConstants.ACCESS_TOKEN_COOKIE);
          return (cookie != null && !cookie.getValue().isBlank())
                  ? cookie.getValue()
                  : headerResolver.resolve(request);
        };
    }

    private JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * 게이트웨이 검증 규칙
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwksUri
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                new JwtTimestampValidator(Duration.ZERO),
                SecurityConfig::validateAccessToken));
        return decoder;
    }

    private static OAuth2TokenValidatorResult validateAccessToken(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("type"))) {
            return invalid("access 토큰이 아닙니다.");
        }
        try {
            if (jwt.getSubject() == null || Long.parseLong(jwt.getSubject()) <= 0) {
                return invalid("사원 ID가 올바르지 않습니다.");
            }
        } catch (NumberFormatException e) {
            return invalid("사원 ID가 올바르지 않습니다.");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult invalid(String description) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
