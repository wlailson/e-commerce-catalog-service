package io.wlailson.github.e_commerce_catalog_service.controller;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=integration-test-secret-with-more-than-32-bytes",
                "cors.origins=http://localhost:3000"
        }
)
@AutoConfigureRestTestClient
class ProductControllerIT extends AbstractIntegrationTest {

    private static final String TEST_SECRET = "integration-test-secret-with-more-than-32-bytes";

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM tb_product_category");
        jdbcTemplate.update("DELETE FROM tb_product");
        jdbcTemplate.update("DELETE FROM tb_category");
    }

    private String token(String... roles) {
        Instant now = Instant.now();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(
                        TEST_SECRET.getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256"
                ))
        );
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("catalog-integration-tests")
                .subject("integration-test-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of(roles))
                .build();

        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
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
                "INSERT INTO tb_category (name) VALUES (?) RETURNING id",
                Long.class,
                name
        ));
    }

    private String productJson(String name, String description, double price, String imgUrl, long categoryId) {
        return """
                {
                  "name": "%s",
                  "description": "%s",
                  "price": %s,
                  "imgUrl": "%s",
                  "categories": [{"id": %d, "name": "ignored request name"}]
                }
                """.formatted(name, description, price, imgUrl, categoryId);
    }

    private ProductDTO createProduct(
            String name,
            String description,
            double price,
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
                    "Wireless Headphones", "Wireless over-ear headphones", 89.99, "headphones.png", categoryId
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
        void requiresAuthentication() {
            client.get()
                    .uri("/products/1")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    class FindAll {

        @Test
        void filtersCaseInsensitivelyAndReturnsPagedResults() {
            long categoryId = createCategory("Electronics");
            createProduct("Wireless Phone", "A wireless phone device", 300.0, "phone.png", categoryId);
            createProduct("Phone Case", "Protective case for phones", 15.0, "case.png", categoryId);
            createProduct("Laptop", "Portable laptop computer", 900.0, "laptop.png", categoryId);

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
            createProduct("Laptop", "Portable laptop computer", 900.0, "laptop.png", categoryId);

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
            createProduct("Phone", "A smart phone device", 300.0, "phone.png", categoryId);
            createProduct("Laptop", "Portable laptop computer", 900.0, "laptop.png", categoryId);

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
        void requiresAuthentication() {
            client.get()
                    .uri("/products")
                    .exchange()
                    .expectStatus().isUnauthorized();
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
                            "Bluetooth Speaker", "Portable bluetooth speaker", 49.99, "speaker.png", categoryId
                    ))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().valueMatches("Location", ".*/products/\\d+$")
                    .expectBody()
                    .jsonPath("$.id").isNumber()
                    .jsonPath("$.name").isEqualTo("Bluetooth Speaker")
                    .jsonPath("$.categories[0].id").isEqualTo(categoryId);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_product WHERE name = ?",
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
                    .jsonPath("$.errors.categories").exists();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_product",
                    Integer.class
            )).isZero();
        }

        @Test
        void returnsNotFoundWhenCategoryDoesNotExist() {
            client.post()
                    .uri("/products")
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(productJson("Product", "A sufficiently long description", 15.0, "", 99999))
                    .exchange()
                    .expectStatus().isNotFound();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_product",
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
                    .body(productJson("Product", "A sufficiently long description", 15.0, "", categoryId))
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
                    "Old product", "Original product description", 20.0, "old.png", categoryId
            );

            client.put()
                    .uri("/products/{id}", created.id())
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(productJson(
                            "Updated product", "Updated product description", 32.5, "updated.png", categoryId
                    ))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(created.id())
                    .jsonPath("$.name").isEqualTo("Updated product")
                    .jsonPath("$.price").isEqualTo(32.5)
                    .jsonPath("$.imgUrl").isEqualTo("updated.png");

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
                    .body(productJson("Product", "A sufficiently long description", 15.0, "", categoryId))
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
                    "Product", "A sufficiently long description", 15.0, "", existingCategoryId
            );

            client.put()
                    .uri("/products/{id}", created.id())
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(productJson(
                            "Updated product", "A sufficiently long description", 16.0, "", 99999
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
                              "categories": [{"id": 1}]
                            }
                            """)
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    class Delete {

        @Test
        void adminDeletesProductAndProductIsNoLongerFound() {
            long categoryId = createCategory("General");
            ProductDTO created = createProduct(
                    "Disposable product", "Product to delete from catalog", 10.0, "", categoryId
            );

            client.delete()
                    .uri("/products/{id}", created.id())
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectBody().isEmpty();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_product WHERE id = ?",
                    Integer.class,
                    created.id()
            )).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tb_product_category WHERE product_id = ?",
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
