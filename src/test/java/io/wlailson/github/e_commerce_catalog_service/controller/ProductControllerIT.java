package io.wlailson.github.e_commerce_catalog_service.controller;

import io.wlailson.github.e_commerce_catalog_service.security.JwtTestConfiguration;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "JWT_PUBLIC_KEY=classpath:jwt-test-public.pem"
)
@AutoConfigureRestTestClient
@Import(JwtTestConfiguration.class)
class ProductControllerIT extends AbstractIntegrationTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM catalog.tb_product_category");
        jdbcTemplate.update("DELETE FROM catalog.tb_product");
        jdbcTemplate.update("DELETE FROM catalog.tb_category");
    }

    private String token(String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("catalog-integration-tests")
                .subject("integration-test-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of(roles))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims
        )).getTokenValue();
    }

    private String adminToken() {
        return token("ROLE_ADMIN");
    }

    private String userToken() {
        return token("ROLE_USER");
    }

    private long createCategory(String name) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "INSERT INTO catalog.tb_category (name) VALUES (?) RETURNING id",
                Long.class,
                name
        ));
    }

    private String productJson(String name, String description, BigDecimal price, String imgUrl, long categoryId) {
        return productJson(name, description, price, 10, imgUrl, categoryId);
    }

    private String productJson(
            String name,
            String description,
            BigDecimal price,
            Integer stock,
            String imgUrl,
            long categoryId
    ) {
        return """
                {
                  "name": "%s",
                  "description": "%s",
                  "price": %s,
                  "imgUrl": "%s",
                  "stock": %d,
                  "categories": [{"id": %d, "name": "ignored request name"}]
                }
                """.formatted(name, description, price, imgUrl, stock, categoryId);
    }

    private ProductDTO createProduct(
            String name,
            String description,
            BigDecimal price,
            String imgUrl,
            long categoryId
    ) {
        ProductDTO created = client.post()
                .uri("/products")
                .headers(headers -> headers.setBearerAuth(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(productJson(name, description, price, imgUrl, categoryId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductDTO.class)
                .returnResult()
                .getResponseBody();
        return Objects.requireNonNull(created);
    }

    @Nested
    class FindById {

        @Test
        void returnsProductAndPersistedCategory() {
            long categoryId = createCategory("Audio");
            ProductDTO created = createProduct(
                    "Wireless Headphones", "Wireless over-ear headphones",
                    new BigDecimal("89.99"), "headphones.png", categoryId
            );

            client.get()
                    .uri("/products/{id}", created.id())
                    .headers(headers -> headers.setBearerAuth(userToken()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(created.id())
                    .jsonPath("$.name").isEqualTo("Wireless Headphones")
                    .jsonPath("$.price").isEqualTo(89.99)
                    .jsonPath("$.stock").isEqualTo(10)
                    .jsonPath("$.categories[0].id").isEqualTo(categoryId)
                    .jsonPath("$.categories[0].name").isEqualTo("Audio");
        }

        @Test
        void returnsNotFoundForUnknownProduct() {
            client.get()
                    .uri("/products/99999")
                    .headers(headers -> headers.setBearerAuth(userToken()))
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        void allowsAnonymousRequest() {
            long categoryId = createCategory("Audio");
            ProductDTO created = createProduct(
                    "Wireless Headphones", "Wireless over-ear headphones",
                    new BigDecimal("89.99"), "headphones.png", categoryId
            );

            client.get()
                    .uri("/products/{id}", created.id())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(created.id())
                    .jsonPath("$.name").isEqualTo("Wireless Headphones");
        }
    }

    @Nested
    class FindAll {

        @Test
        void filtersCaseInsensitivelyAndReturnsPagedResults() {
            long categoryId = createCategory("Electronics");
            createProduct("Wireless Phone", "A wireless phone device", new BigDecimal("300.00"), "phone.png", categoryId);
            createProduct("Phone Case", "Protective case for phones", new BigDecimal("15.00"), "case.png", categoryId);
            createProduct("Laptop", "Portable laptop computer", new BigDecimal("900.00"), "laptop.png", categoryId);

            client.get()
                    .uri(uriBuilder -> uriBuilder.path("/products")
                            .queryParam("name", "pHoNe")
                            .queryParam("page", 0)
                            .queryParam("size", 1)
                            .queryParam("sort", "name,asc")
                            .build())
                    .headers(headers -> headers.setBearerAuth(userToken()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.content[0].name").isEqualTo("Phone Case")
                    .jsonPath("$.totalElements").isEqualTo(2)
                    .jsonPath("$.totalPages").isEqualTo(2)
                    .jsonPath("$.number").isEqualTo(0)
                    .jsonPath("$.size").isEqualTo(1);
        }

        @Test
        void returnsEmptyPageWhenSearchHasNoMatches() {
            long categoryId = createCategory("Electronics");
            createProduct("Laptop", "Portable laptop computer", new BigDecimal("900.00"), "laptop.png", categoryId);

            client.get()
                    .uri(uriBuilder -> uriBuilder.path("/products")
                            .queryParam("name", "camera")
                            .build())
                    .headers(headers -> headers.setBearerAuth(userToken()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(0)
                    .jsonPath("$.totalElements").isEqualTo(0);
        }

        @Test
        void emptySearchReturnsAllProducts() {
            long categoryId = createCategory("General");
            createProduct("Phone", "A smart phone device", new BigDecimal("300.00"), "phone.png", categoryId);
            createProduct("Laptop", "Portable laptop computer", new BigDecimal("900.00"), "laptop.png", categoryId);

            client.get()
                    .uri("/products")
                    .headers(headers -> headers.setBearerAuth(userToken()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(2)
                    .jsonPath("$.totalElements").isEqualTo(2);
        }

        @Test
        void allowsAnonymousRequest() {
            client.get()
                    .uri("/products")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(0)
                    .jsonPath("$.totalElements").isEqualTo(0);
        }
    }

    @Nested
    class Insert {

        @Test
        void adminCreatesProductAndReturnsLocation() {
            long categoryId = createCategory("Audio");

            client.post()
                    .uri("/products")
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(productJson(
                            "Bluetooth Speaker", "Portable bluetooth speaker",
                            new BigDecimal("49.99"), "speaker.png", categoryId
                    ))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().valueMatches("Location", ".*/products/\\d+$")
                    .expectBody()
                    .jsonPath("$.id").isNumber()
                    .jsonPath("$.name").isEqualTo("Bluetooth Speaker")
                    .jsonPath("$.categories[0].id").isEqualTo(categoryId)
                    .jsonPath("$.stock").isEqualTo(10);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM catalog.tb_product WHERE name = ?",
                    Integer.class,
                    "Bluetooth Speaker"
            )).isEqualTo(1);
        }

        @Test
        void rejectsInvalidPayload() {
            client.post()
                    .uri("/products")
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                              "name": "",
                              "description": "short",
                              "price": 0,
                              "categories": []
                            }
                            """)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errors.name").exists()
                    .jsonPath("$.errors.description").exists()
                    .jsonPath("$.errors.price").exists()
                    .jsonPath("$.errors.stock").exists()
                    .jsonPath("$.errors.categories").exists();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM catalog.tb_product",
                    Integer.class
            )).isZero();
        }

        @Test
        void databaseRejectsNullStock() {
            long categoryId = createCategory("General");
            ProductDTO created = createProduct(
                    "Product", "A sufficiently long description", new BigDecimal("15.00"), "", categoryId
            );

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE catalog.tb_product SET stock = NULL WHERE id = ?",
                    created.id()
            )).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }

        @Test
        void returnsNotFoundWhenCategoryDoesNotExist() {
            client.post()
                    .uri("/products")
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(productJson(
                            "Product", "A sufficiently long description", new BigDecimal("15.00"), "", 99999
                    ))
                    .exchange()
                    .expectStatus().isNotFound();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM catalog.tb_product",
                    Integer.class
            )).isZero();
        }

        @Test
        void rejectsUnauthenticatedRequest() {
            client.post()
                    .uri("/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                              "name": "Product",
                              "description": "A sufficiently long description",
                              "price": 15.0,
                              "categories": [{"id": 1}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void rejectsAuthenticatedNonAdmin() {
            long categoryId = createCategory("General");

            client.post()
                    .uri("/products")
                    .headers(headers -> headers.setBearerAuth(userToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(productJson(
                            "Product", "A sufficiently long description", new BigDecimal("15.00"), "", categoryId
                    ))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    class Update {

        @Test
        void adminUpdatesPersistedProduct() {
            long categoryId = createCategory("General");
            ProductDTO created = createProduct(
                    "Old product", "Original product description", new BigDecimal("20.00"), "old.png", categoryId
            );

            client.put()
                    .uri("/products/{id}", created.id())
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(productJson(
                            "Updated product", "Updated product description",
                            new BigDecimal("32.50"), 22, "updated.png", categoryId
                    ))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(created.id())
                    .jsonPath("$.name").isEqualTo("Updated product")
                    .jsonPath("$.price").isEqualTo(32.5)
                    .jsonPath("$.imgUrl").isEqualTo("updated.png")
                    .jsonPath("$.stock").isEqualTo(22);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT stock FROM catalog.tb_product WHERE id = ?",
                    Integer.class,
                    created.id()
            )).isEqualTo(22);

            client.get()
                    .uri("/products/{id}", created.id())
                    .headers(headers -> headers.setBearerAuth(userToken()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.name").isEqualTo("Updated product");
        }

        @Test
        void returnsNotFoundWhenProductDoesNotExist() {
            long categoryId = createCategory("General");

            client.put()
                    .uri("/products/99999")
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(productJson(
                            "Product", "A sufficiently long description", new BigDecimal("15.00"), "", categoryId
                    ))
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        void rejectsInvalidPayload() {
            client.put()
                    .uri("/products/1")
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                              "name": "x",
                              "description": "",
                              "price": null,
                              "categories": []
                            }
                            """)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errors.name").exists()
                    .jsonPath("$.errors.description").exists()
                    .jsonPath("$.errors.price").exists()
                    .jsonPath("$.errors.categories").exists();
        }

        @Test
        void returnsNotFoundWhenCategoryDoesNotExist() {
            long existingCategoryId = createCategory("General");
            ProductDTO created = createProduct(
                    "Product", "A sufficiently long description", new BigDecimal("15.00"), "", existingCategoryId
            );

            client.put()
                    .uri("/products/{id}", created.id())
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(productJson(
                            "Updated product", "A sufficiently long description", new BigDecimal("16.00"), "", 99999
                    ))
                    .exchange()
                    .expectStatus().isNotFound();

            client.get()
                    .uri("/products/{id}", created.id())
                    .headers(headers -> headers.setBearerAuth(userToken()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.name").isEqualTo("Product");
        }

        @Test
        void rejectsUnauthenticatedRequest() {
            client.put()
                    .uri("/products/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                              "name": "Updated product",
                              "description": "A sufficiently long description",
                              "price": 15.0,
                              "stock": 10,
                              "categories": [{"id": 1}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void rejectsAuthenticatedNonAdmin() {
            client.put()
                    .uri("/products/1")
                    .headers(headers -> headers.setBearerAuth(userToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {
                              "name": "Updated product",
                              "description": "A sufficiently long description",
                              "price": 15.0,
                              "stock": 10,
                              "categories": [{"id": 1}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    class AddStock {

        @Test
        void adminAddsQuantityToPersistedStock() {
            long categoryId = createCategory("General");
            ProductDTO created = createProduct(
                    "Product", "A sufficiently long description", new BigDecimal("15.00"), "", categoryId
            );

            client.patch()
                    .uri("/products/{id}/stock", created.id())
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("5")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.stock").isEqualTo(15);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT stock FROM catalog.tb_product WHERE id = ?",
                    Integer.class,
                    created.id()
            )).isEqualTo(15);
        }

        @Test
        void rejectsZeroAndNegativeQuantityWithoutChangingStock() {
            long categoryId = createCategory("General");
            ProductDTO created = createProduct(
                    "Product", "A sufficiently long description", new BigDecimal("15.00"), "", categoryId
            );

            for (String quantity : List.of("0", "-3")) {
                client.patch()
                        .uri("/products/{id}/stock", created.id())
                        .headers(headers -> headers.setBearerAuth(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(quantity)
                        .exchange()
                        .expectStatus().isBadRequest();
            }

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT stock FROM catalog.tb_product WHERE id = ?",
                    Integer.class,
                    created.id()
            )).isEqualTo(10);
        }

        @Test
        void returnsNotFoundForUnknownProduct() {
            client.patch()
                    .uri("/products/99999/stock")
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("5")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    class Delete {

        @Test
        void adminDeletesProductAndProductIsNoLongerFound() {
            long categoryId = createCategory("General");
            ProductDTO created = createProduct(
                    "Disposable product", "Product to delete from catalog", new BigDecimal("10.00"), "", categoryId
            );

            client.delete()
                    .uri("/products/{id}", created.id())
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectBody().isEmpty();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM catalog.tb_product WHERE id = ?",
                    Integer.class,
                    created.id()
            )).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM catalog.tb_product_category WHERE product_id = ?",
                    Integer.class,
                    created.id()
            )).isZero();
        }

        @Test
        void returnsNotFoundWhenProductDoesNotExist() {
            client.delete()
                    .uri("/products/99999")
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        void rejectsUnauthenticatedRequest() {
            client.delete()
                    .uri("/products/1")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void rejectsAuthenticatedNonAdmin() {
            client.delete()
                    .uri("/products/1")
                    .headers(headers -> headers.setBearerAuth(userToken()))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }
}
