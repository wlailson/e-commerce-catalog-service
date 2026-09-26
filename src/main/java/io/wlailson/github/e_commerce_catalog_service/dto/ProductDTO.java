package io.wlailson.github.e_commerce_catalog_service.dto;

import io.wlailson.github.e_commerce_catalog_service.domain.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductDTO(
        Long id,
        @NotBlank(message = "Campo requerido")
        @Size(min = 3, max = 80, message = "Nome precisa ter de 3 a 80 caracteres")
        String name,
        @NotBlank(message = "Campo requerido")
        @Size(min = 10, message = "Descrição precisa ter no mínimo 10 caracteres")
        String description,
        @NotNull(message = "Preço é obrigatório")
        @Positive(message = "O preço deve ser positivo")
        Double price,
        String imgUrl,
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
