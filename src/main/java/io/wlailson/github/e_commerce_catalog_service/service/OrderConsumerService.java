package io.wlailson.github.e_commerce_catalog_service.service;

import io.wlailson.github.e_commerce_catalog_service.message.OrderCreateMessage;
import io.wlailson.github.e_commerce_catalog_service.message.OrderEvent;
import io.wlailson.github.e_commerce_catalog_service.message.OrderUpdatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderConsumerService {

    private final ProductService productService;

    @KafkaListener(topics = "${app.kafka.topics.order-created}")
    public void orderCreated(OrderCreateMessage message) {
        log.info("Received order-created event: orderId={}", message.orderId());
        productService.processOrderCreated(message);
    }

    @KafkaListener(topics = "${app.kafka.topics.order-updated}")
    public void orderUpdated(OrderUpdatedMessage message) {
        log.info(
                "Received order-updated event: orderId={}, event={}",
                message.orderId(),
                message.event()
        );

        if (message.event() == OrderEvent.PAY) {
            productService.confirmReservations(message.orderId());
        } else if (message.event() == OrderEvent.CANCEL) {
            productService.releaseReservation(message.orderId());
        }
    }
}