package io.wlailson.github.e_commerce_catalog_service.service;

import io.wlailson.github.e_commerce_catalog_service.message.OrderCreateMessage;
import io.wlailson.github.e_commerce_catalog_service.message.OrderUpdatedMessage;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConsumerServiceTest {

    @Test
    void listensToConfiguredOrderEventTopics() throws NoSuchMethodException {
        KafkaListener createdListener = OrderConsumerService.class
                .getMethod("orderCreated", OrderCreateMessage.class)
                .getAnnotation(KafkaListener.class);
        KafkaListener updatedListener = OrderConsumerService.class
                .getMethod("orderUpdated", OrderUpdatedMessage.class)
                .getAnnotation(KafkaListener.class);

        assertThat(createdListener.topics()).containsExactly("${app.kafka.topics.order-created}");
        assertThat(updatedListener.topics()).containsExactly("${app.kafka.topics.order-updated}");
    }
}
