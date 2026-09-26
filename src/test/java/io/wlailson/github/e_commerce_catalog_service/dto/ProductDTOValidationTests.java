package io.wlailson.github.e_commerce_catalog_service.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDTOValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requiresPrice() {
        ProductDTO dto = new ProductDTO(
                null,
                "Valid product",
                "A sufficiently long product description",
                null,
                null,
                List.of(new CategoryDTO(1L, "Category"))
        );

        assertTrue(validator.validate(dto).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("price")));
    }

    @Test
    void validatesCategoryIdsInRequest() {
        ProductDTO dto = new ProductDTO(
                null,
                "Valid product",
                "A sufficiently long product description",
                10.0,
                null,
                List.of(new CategoryDTO(null, "Category"))
        );

        assertTrue(validator.validate(dto).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("categories[0].id")));
    }
}
