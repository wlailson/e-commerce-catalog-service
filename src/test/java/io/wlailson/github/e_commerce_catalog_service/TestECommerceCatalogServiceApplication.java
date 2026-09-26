package io.wlailson.github.e_commerce_catalog_service;

import org.springframework.boot.SpringApplication;

public class TestECommerceCatalogServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(Application::main).with(TestcontainersConfiguration.class).run(args);
	}

}
