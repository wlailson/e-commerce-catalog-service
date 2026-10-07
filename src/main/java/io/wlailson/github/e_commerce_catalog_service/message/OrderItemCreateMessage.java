package io.wlailson.github.e_commerce_catalog_service.message;


public record OrderItemCreateMessage(Long productId, Integer quantity) {
}
