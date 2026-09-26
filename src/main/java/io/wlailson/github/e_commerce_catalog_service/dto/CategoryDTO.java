package io.wlailson.github.e_commerce_catalog_service.dto;

import io.wlailson.github.e_commerce_catalog_service.domain.Category;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CategoryDTO(
        @NotNull(message = "Id da categoria é obrigatório")
        @Positive(message = "Id da categoria deve ser positivo")
        Long id,
        String name) {

    public CategoryDTO(Category entity) {
        this(entity.getId(), entity.getName());
    }
}
