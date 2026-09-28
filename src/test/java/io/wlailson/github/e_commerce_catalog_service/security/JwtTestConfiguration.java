package io.wlailson.github.e_commerce_catalog_service.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@TestConfiguration(proxyBeanMethods = false)
public class JwtTestConfiguration {

    @Bean
    KeyPair testRsaKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Bean
    JwtDecoder jwtDecoder(KeyPair testRsaKeyPair) {
        return NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) testRsaKeyPair.getPublic())
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair testRsaKeyPair) {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) testRsaKeyPair.getPublic())
                .privateKey((RSAPrivateKey) testRsaKeyPair.getPrivate())
                .keyID("catalog-test-key")
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }
}
