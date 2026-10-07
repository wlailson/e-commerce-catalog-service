package io.wlailson.github.e_commerce_catalog_service.repository;

import io.wlailson.github.e_commerce_catalog_service.domain.Product;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private ProductRepository productRepository;

    private Product product(String name) {
        return product(name, 8);
    }

    private Product product(String name, int stock) {
        return new Product(
                null,
                name,
                "A sufficiently long product description",
                new BigDecimal("10.00"),
                null,
                stock,
                0,
                new HashSet<>()
        );
    }

    @Nested
    class SearchByName {

        @Test
        void findsProductsByCaseInsensitivePartialName() {
            productRepository.saveAllAndFlush(List.of(
                    product("Wireless Phone Case"),
                    product("Phone Stand"),
                    product("Laptop Sleeve")
            ));

            Page<Product> result = productRepository.searchByName(
                    "pHoNe",
                    PageRequest.of(0, 10, Sort.by("name").ascending())
            );

            assertThat(result.getContent())
                    .extracting(Product::getName)
                    .containsExactly("Phone Stand", "Wireless Phone Case");
            assertThat(result.getContent())
                    .extracting(Product::getPrice)
                    .containsOnly(new BigDecimal("10.00"));
            assertThat(result.getContent())
                    .extracting(Product::getStock)
                    .containsOnly(8);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        void returnsAllProductsWhenSearchTermIsEmpty() {
            productRepository.saveAllAndFlush(List.of(
                    product("Phone"),
                    product("Laptop"),
                    product("Headphones")
            ));

            Page<Product> result = productRepository.searchByName("", PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getTotalElements()).isEqualTo(3);
        }

        @Test
        void includesOutOfStockProductsInSearch() {
            productRepository.saveAllAndFlush(List.of(
                    product("Available Phone", 3),
                    product("Unavailable Phone", 0),
                    product("Available Laptop", 1)
            ));

            Page<Product> result = productRepository.searchByName(
                    "phone",
                    PageRequest.of(0, 10)
            );

            assertThat(result.getContent())
                    .extracting(Product::getName)
                    .containsExactlyInAnyOrder("Available Phone", "Unavailable Phone");
            assertThat(result.getContent())
                    .extracting(Product::getStock)
                    .contains(0);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        void returnsEmptyPageWhenThereAreNoMatches() {
            productRepository.saveAllAndFlush(List.of(
                    product("Phone"),
                    product("Laptop")
            ));

            Page<Product> result = productRepository.searchByName("camera", PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        void appliesPageAndSortToSearchResults() {
            productRepository.saveAllAndFlush(List.of(
                    product("Phone C"),
                    product("Phone A"),
                    product("Phone B")
            ));

            Page<Product> firstPage = productRepository.searchByName(
                    "phone",
                    PageRequest.of(0, 2, Sort.by("name").ascending())
            );
            Page<Product> secondPage = productRepository.searchByName(
                    "phone",
                    PageRequest.of(1, 2, Sort.by("name").ascending())
            );

            assertThat(firstPage.getContent())
                    .extracting(Product::getName)
                    .containsExactly("Phone A", "Phone B");
            assertThat(firstPage.getTotalElements()).isEqualTo(3);
            assertThat(firstPage.getTotalPages()).isEqualTo(2);
            assertThat(secondPage.getContent())
                    .extracting(Product::getName)
                    .containsExactly("Phone C");
            assertThat(secondPage.getTotalElements()).isEqualTo(3);
        }

        @Test
        void findsProductByIdRegardlessOfStock() {
            Product outOfStock = productRepository.saveAndFlush(product("Unavailable", 0));
            Product inStock = productRepository.saveAndFlush(product("Available", 2));

            assertThat(productRepository.findById(outOfStock.getId()))
                    .get()
                    .extracting(Product::getStock)
                    .isEqualTo(0);
            assertThat(productRepository.findById(inStock.getId()))
                    .get()
                    .extracting(Product::getName)
                    .isEqualTo("Available");
        }
    }
}
