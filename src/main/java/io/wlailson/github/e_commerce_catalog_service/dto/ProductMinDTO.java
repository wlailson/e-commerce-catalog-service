package io.wlailson.github.e_commerce_catalog_service.dto;

public record ProductMinDTO(
        Long id,
        String name,
        Double price,
        String imgUrl) {
}
