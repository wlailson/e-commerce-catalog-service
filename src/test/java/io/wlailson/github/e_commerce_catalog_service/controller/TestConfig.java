package io.wlailson.github.e_commerce_catalog_service.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.context.WebApplicationContext;

@TestConfiguration(proxyBeanMethods = false)
public class TestConfig {

    @Bean
    RestTestClient restTestClient(
            WebApplicationContext context) {

        return RestTestClient.bindToApplicationContext(context)
                .build();
    }
}
