package io.wlailson.github.e_commerce_catalog_service.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {

        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 UTF-8 bytes");
        }
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");

        return NimbusJwtDecoder
                .withSecretKey(key)
                .build();
    }
}