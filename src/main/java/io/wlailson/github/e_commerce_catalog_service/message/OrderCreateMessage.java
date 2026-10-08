package io.wlailson.github.e_commerce_catalog_service.message;



import java.time.Instant;
import java.util.Set;

public record OrderCreateMessage(
        Long orderId,
        OrderEvent event,
        Instant occurredAt,
        Instant expiresAt,
        Set<OrderItemCreateMessage> items
) {
}
