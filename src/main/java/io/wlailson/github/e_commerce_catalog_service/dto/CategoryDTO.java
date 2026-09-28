package io.wlailson.github.e_commerce_catalog_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.wlailson.github.e_commerce_catalog_service.domain.Category;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Categoria associada a um produto")
public record CategoryDTO(
        @Schema(description = "Identificador da categoria existente", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Id da categoria é obrigatório")
        @Positive(message = "Id da categoria deve ser positivo")
        Long id,
        @Schema(description = "Nome da categoria; retornado na resposta e ignorado na associação de categorias", example = "Eletrônicos", accessMode = Schema.AccessMode.READ_ONLY)
        String name) {

    public CategoryDTO(Category entity) {
        this(entity.getId(), entity.getName());
    }
}
