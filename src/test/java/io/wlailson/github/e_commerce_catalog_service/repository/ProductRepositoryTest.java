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
        return new Product(
                null,
                name,
                "A sufficiently long product description",
                10.0,
                null,
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
    }
}
