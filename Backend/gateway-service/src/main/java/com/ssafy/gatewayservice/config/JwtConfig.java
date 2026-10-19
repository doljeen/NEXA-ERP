package com.ssafy.gatewayservice.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${security.jwt.secret-base64}") String secretBase64,
            @Value("${security.jwt.issuer}") String issuer
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(secretBase64);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT Secret Key는 최소 32바이트여야 합니다."
            );
        }

        SecretKey secretKey =
                new SecretKeySpec(keyBytes, "HmacSHA256");

        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder
                        .withSecretKey(secretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(issuer)
        );

        return decoder;
    }
}
