package io.wlailson.github.e_commerce_catalog_service.controller;

import io.wlailson.github.e_commerce_catalog_service.dto.CategoryDTO;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductDTO;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductMinDTO;
import io.wlailson.github.e_commerce_catalog_service.exceptions.ResourceNotFoundException;
import io.wlailson.github.e_commerce_catalog_service.config.SecurityConfig;
import io.wlailson.github.e_commerce_catalog_service.service.ProductService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ProductController.class,
        properties = "JWT_PUBLIC_KEY=classpath:jwt-test-public.pem"
)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static ProductDTO productDTO() {
        return new ProductDTO(
                10L,
                "Product name",
                "A valid product description",
                new BigDecimal("25.50"),
                "https://example.com/product.png",
                12,
                2,
                List.of(new CategoryDTO(3L, "Category"))
        );
    }

    private static String validProductJson() {
        return """
                {
                  "name": "Product name",
                  "description": "A valid product description",
                  "price": 25.50,
                  "imgUrl": "https://example.com/product.png",
                  "stock": 12,
                  "categories": [{"id": 3, "name": "Category"}]
                }
                """;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Nested
    class FindById {

        @Test
        void returnsProductForAuthenticatedRequest() throws Exception {
            when(service.findProductById(10L)).thenReturn(productDTO());

            mockMvc.perform(get("/products/10").with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.name").value("Product name"))
                    .andExpect(jsonPath("$.price").value(25.50))
                    .andExpect(jsonPath("$.stock").value(12))
                    .andExpect(jsonPath("$.reservedStock").value(2))
                    .andExpect(jsonPath("$.categories[0].id").value(3));

            verify(service).findProductById(10L);
        }

        @Test
        void returnsOutOfStockProductForPublicRequest() throws Exception {
            ProductDTO outOfStockProduct = new ProductDTO(
                    10L,
                    "Product name",
                    "A valid product description",
                    new BigDecimal("25.50"),
                    "https://example.com/product.png",
                    0,
                    0,
                    List.of(new CategoryDTO(3L, "Category"))
            );
            when(service.findProductById(10L)).thenReturn(outOfStockProduct);

            mockMvc.perform(get("/products/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stock").value(0));

            verify(service).findProductById(10L);
        }

        @Test
        void returnsNotFoundWhenProductDoesNotExist() throws Exception {
            when(service.findProductById(404L))
                    .thenThrow(new ResourceNotFoundException("Produto não encontrado"));

            mockMvc.perform(get("/products/404").with(jwt()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void allowsAnonymousRequest() throws Exception {
            when(service.findProductById(10L)).thenReturn(productDTO());

            mockMvc.perform(get("/products/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));

            verify(service).findProductById(10L);
        }
    }

    @Nested
    class FindAll {

        @Test
        void returnsPagedProductsForAuthenticatedRequest() throws Exception {
            PageRequest pageable = PageRequest.of(1, 2);
            when(service.findAllProductsByName(eq("phone"), any()))
                    .thenReturn(new PageImpl<>(
                            List.of(new ProductMinDTO(10L, "Phone", new BigDecimal("99.99"), "phone.png", 7, 1)),
                            pageable,
                            3
                    ));

            mockMvc.perform(get("/products")
                            .param("name", "phone")
                            .param("page", "1")
                            .param("size", "2")
                            .with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(10))
                    .andExpect(jsonPath("$.content[0].name").value("Phone"))
                    .andExpect(jsonPath("$.content[0].price").value(99.99))
                    .andExpect(jsonPath("$.content[0].stock").value(7))
                    .andExpect(jsonPath("$.content[0].reservedStock").value(1));

            verify(service).findAllProductsByName(eq("phone"), eq(pageable));
        }

        @Test
        void returnsOutOfStockProductsForPublicRequest() throws Exception {
            PageRequest pageable = PageRequest.of(0, 10);
            when(service.findAllProductsByName(eq(""), eq(pageable)))
                    .thenReturn(new PageImpl<>(
                            List.of(new ProductMinDTO(10L, "Out of stock", BigDecimal.ONE, null, 0, 0)),
                            pageable,
                            1
                    ));

            mockMvc.perform(get("/products")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].stock").value(0));

            verify(service).findAllProductsByName("", pageable);
        }

        @Test
        void allowsAnonymousRequest() throws Exception {
            when(service.findAllProductsByName(eq(""), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());

            verify(service).findAllProductsByName(eq(""), any());
        }
    }

    @Nested
    class Insert {

        @Test
        void createsProductForAdmin() throws Exception {
            when(service.insertProduct(any(ProductDTO.class))).thenReturn(productDTO());

            mockMvc.perform(post("/products")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/products/10"))
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.name").value("Product name"));

            verify(service).insertProduct(any(ProductDTO.class));
        }

        @Test
        void rejectsInvalidProductPayload() throws Exception {
            mockMvc.perform(post("/products")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "",
                                      "description": "short",
                                      "price": null,
                                      "categories": []
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("One or more fields are invalid"))
                    .andExpect(jsonPath("$.errors.name").exists());

            verify(service, never()).insertProduct(any());
        }

        @Test
        void rejectsUnauthenticatedRequest() throws Exception {
            mockMvc.perform(post("/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isUnauthorized());

            verify(service, never()).insertProduct(any());
        }

        @Test
        void rejectsNonAdminRequest() throws Exception {
            mockMvc.perform(post("/products")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isForbidden());

            verify(service, never()).insertProduct(any());
        }
    }

    @Nested
    class Update {

        @Test
        void updatesProductForAdmin() throws Exception {
            when(service.updateProduct(eq(10L), any(ProductDTO.class))).thenReturn(productDTO());

            mockMvc.perform(put("/products/10")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.name").value("Product name"));

            verify(service).updateProduct(eq(10L), any(ProductDTO.class));
        }

        @Test
        void rejectsUnauthenticatedRequest() throws Exception {
            mockMvc.perform(put("/products/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isUnauthorized());

            verify(service, never()).updateProduct(any(), any());
        }

        @Test
        void rejectsNonAdminRequest() throws Exception {
            mockMvc.perform(put("/products/10")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isForbidden());

            verify(service, never()).updateProduct(any(), any());
        }
    }

    @Nested
    class AddStock {

        @Test
        void addsStockForAdmin() throws Exception {
            when(service.addStock(10L, 5)).thenReturn(productDTO());

            mockMvc.perform(patch("/products/10/stock")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stock").value(12));

            verify(service).addStock(10L, 5);
        }

        @Test
        void rejectsUnauthenticatedRequest() throws Exception {
            mockMvc.perform(patch("/products/10/stock")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("5"))
                    .andExpect(status().isUnauthorized());

            verify(service, never()).addStock(any(), any());
        }

        @Test
        void rejectsNonAdminRequest() throws Exception {
            mockMvc.perform(patch("/products/10/stock")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("5"))
                    .andExpect(status().isForbidden());

            verify(service, never()).addStock(any(), any());
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesProductForAdmin() throws Exception {
            mockMvc.perform(delete("/products/10").with(adminJwt()))
                    .andExpect(status().isNoContent());

            verify(service).deleteProductById(10L);
        }

        @Test
        void rejectsUnauthenticatedRequest() throws Exception {
            mockMvc.perform(delete("/products/10"))
                    .andExpect(status().isUnauthorized());

            verify(service, never()).deleteProductById(any());
        }

        @Test
        void rejectsNonAdminRequest() throws Exception {
            mockMvc.perform(delete("/products/10").with(jwt()))
                    .andExpect(status().isForbidden());

            verify(service, never()).deleteProductById(any());
        }
    }
}
