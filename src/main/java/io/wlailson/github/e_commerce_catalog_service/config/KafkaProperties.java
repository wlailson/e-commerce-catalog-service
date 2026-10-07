package io.wlailson.github.e_commerce_catalog_service.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaProperties(
        @NotNull @Valid Topics topics
) {
    public record Topics(
            @NotBlank String orderCreated,
            @NotBlank String orderUpdated
    ) {
    }
}
