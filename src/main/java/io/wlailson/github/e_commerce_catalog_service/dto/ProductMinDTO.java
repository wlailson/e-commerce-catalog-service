package io.wlailson.github.e_commerce_catalog_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Representação resumida de um produto nas listagens")
public record ProductMinDTO(
        @Schema(description = "Identificador do produto", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
        Long id,
        @Schema(description = "Nome do produto", example = "Smartphone", accessMode = Schema.AccessMode.READ_ONLY)
        String name,
        @Schema(description = "Preço unitário", example = "2499.90", accessMode = Schema.AccessMode.READ_ONLY)
        Double price,
        @Schema(description = "URL da imagem do produto", example = "https://example.com/images/smartphone.jpg", nullable = true, accessMode = Schema.AccessMode.READ_ONLY)
        String imgUrl) {
}
