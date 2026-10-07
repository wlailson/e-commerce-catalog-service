package io.wlailson.github.e_commerce_catalog_service.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
                null,
                null,
                List.of(new CategoryDTO(1L, "Category"))
        );

        assertTrue(validator.validate(dto).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("price")
                        && violation.getMessage().equals("Price is required")));
    }

    @Test
    void requiresStock() {
        ProductDTO dto = new ProductDTO(
                null,
                "Valid product",
                "A sufficiently long product description",
                new BigDecimal("10.00"),
                null,
                null,
                null,
                List.of(new CategoryDTO(1L, "Category"))
        );

        assertTrue(validator.validate(dto).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("stock")
                        && violation.getMessage().equals("Stock is required")));
    }

    @Test
    void rejectsNegativeStock() {
        ProductDTO dto = new ProductDTO(
                null,
                "Valid product",
                "A sufficiently long product description",
                new BigDecimal("10.00"),
                null,
                -1,
                null,
                List.of(new CategoryDTO(1L, "Category"))
        );

        assertTrue(validator.validate(dto).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("stock")
                        && violation.getMessage().equals("Stock must be zero or greater")));
    }

    @Test
    void validatesCategoryIdsInRequest() {
        ProductDTO dto = new ProductDTO(
                null,
                "Valid product",
                "A sufficiently long product description",
                new BigDecimal("10.00"),
                null,
                10,
                null,
                List.of(new CategoryDTO(null, "Category"))
        );

        assertTrue(validator.validate(dto).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("categories[0].id")
                        && violation.getMessage().equals("Category ID is required")));
    }

    @Test
    void reportsEnglishValidationMessages() {
        ProductDTO dto = new ProductDTO(
                null,
                "",
                "short",
                BigDecimal.ZERO,
                null,
                -1,
                null,
                List.of(new CategoryDTO(-1L, "Category"))
        );

        List<String> messages = validator.validate(dto).stream()
                .map(violation -> violation.getMessage())
                .toList();

        assertTrue(messages.containsAll(List.of(
                "Name is required",
                "Name must be between 3 and 80 characters",
                "Description must be at least 10 characters",
                "Price must be positive",
                "Stock must be zero or greater",
                "Category ID must be positive"
        )));
    }
}
