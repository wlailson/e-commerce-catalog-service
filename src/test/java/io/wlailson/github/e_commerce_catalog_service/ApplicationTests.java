package io.wlailson.github.e_commerce_catalog_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApplicationTests {

	@DynamicPropertySource
	static void applicationProperties(DynamicPropertyRegistry registry) {
		registry.add("jwt.secret", () -> "catalog-test-secret-value-with-32-bytes");
		registry.add("cors.origins", () -> "http://localhost:3000");
	}

	@Test
	void contextLoads() {
	}

}
