package io.wlailson.github.e_commerce_catalog_service;

import io.wlailson.github.e_commerce_catalog_service.security.JwtTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Import({TestcontainersConfiguration.class, JwtTestConfiguration.class})
@SpringBootTest
class ApplicationTests {

	@DynamicPropertySource
	static void applicationProperties(DynamicPropertyRegistry registry) {
		registry.add("JWT_PUBLIC_KEY", () -> "classpath:jwt-test-public.pem");
	}

	@Test
	void contextLoads() {
	}

}
