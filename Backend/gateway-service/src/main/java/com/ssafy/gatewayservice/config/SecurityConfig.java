
package com.ssafy.gatewayservice.config;

import com.ssafy.gatewayservice.exception.GatewayErrorWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            CorsConfigurationSource corsSource,
            GatewayErrorWriter errorWriter
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsSource))
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(
                                "/api/{version:v[0-9]+}/auth/login",
                                "/api/{version:v[0-9]+}/auth/refresh"
                        ).permitAll()
                        .anyExchange().authenticated()
                )

                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().getHeaders()
                                    .set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                            return errorWriter.write(
                                    exchange,
                                    HttpStatus.UNAUTHORIZED,
                                    "UNAUTHORIZED",
                                    "인증이 필요하거나 토큰이 유효하지 않습니다."
                            );
                        })
                        .accessDeniedHandler((exchange, ex) ->
                                errorWriter.write(
                                        exchange,
                                        HttpStatus.FORBIDDEN,
                                        "FORBIDDEN",
                                        "접근 권한이 없습니다."
                                )
                        )
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().getHeaders()
                                    .set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                            return errorWriter.write(
                                    exchange,
                                    HttpStatus.UNAUTHORIZED,
                                    "UNAUTHORIZED",
                                    "인증이 필요하거나 토큰이 유효하지 않습니다."
                            );
                        })
                        .jwt(withDefaults())
                )

                .build();
    }
}
