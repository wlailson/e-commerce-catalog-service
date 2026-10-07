package io.wlailson.github.e_commerce_catalog_service.message;

import java.time.Instant;

public record OrderUpdatedMessage(
        Long orderId,
        OrderEvent event,
        Instant occurredAt
) {
}
