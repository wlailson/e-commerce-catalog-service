package io.wlailson.github.e_commerce_catalog_service.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsConfiguredTopicNames() {
        KafkaProperties properties = new KafkaProperties(
                new KafkaProperties.Topics("order-created-event", "order-updated-event")
        );

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsMissingOrBlankTopicNames() {
        KafkaProperties properties = new KafkaProperties(
                new KafkaProperties.Topics(" ", "")
        );

        assertThat(validator.validate(properties)).hasSize(2);
        assertThat(validator.validate(new KafkaProperties(null))).hasSize(1);
    }
}
