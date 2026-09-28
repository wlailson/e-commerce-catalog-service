package io.wlailson.github.e_commerce_catalog_service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.wlailson.github.e_commerce_catalog_service.domain.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Dados completos de um produto")
public record ProductDTO(
        @Schema(description = "Identificador gerado para o produto", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
        Long id,
        @Schema(description = "Nome do produto", example = "Smartphone", minLength = 3, maxLength = 80, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Campo requerido")
        @Size(min = 3, max = 80, message = "Nome precisa ter de 3 a 80 caracteres")
        String name,
        @Schema(description = "Descrição do produto", example = "Smartphone com tela de alta resolução", minLength = 10, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Campo requerido")
        @Size(min = 10, message = "Descrição precisa ter no mínimo 10 caracteres")
        String description,
        @Schema(description = "Preço unitário, deve ser maior que zero", example = "2499.90", exclusiveMinimum = true, minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Preço é obrigatório")
        @Positive(message = "O preço deve ser positivo")
        Double price,
        @Schema(description = "URL da imagem do produto", example = "https://example.com/images/smartphone.jpg", nullable = true)
        String imgUrl,
        @Schema(description = "Categorias existentes associadas ao produto; deve conter pelo menos uma categoria", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Deve ter pelo menos uma categoria")
        List<@NotNull @Valid CategoryDTO> categories) {

    public ProductDTO(Product entity) {
        this(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getImgUrl(),
                entity.getCategories().stream().map(CategoryDTO::new).toList()
        );
    }
}
