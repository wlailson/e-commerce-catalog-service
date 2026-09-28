package io.wlailson.github.e_commerce_catalog_service.controller;

import io.wlailson.github.e_commerce_catalog_service.dto.CategoryDTO;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductDTO;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductMinDTO;
import io.wlailson.github.e_commerce_catalog_service.exceptions.ResourceNotFoundException;
import io.wlailson.github.e_commerce_catalog_service.security.SecurityConfig;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                25.50,
                "https://example.com/product.png",
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
            when(service.findById(10L)).thenReturn(productDTO());

            mockMvc.perform(get("/products/10").with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.name").value("Product name"))
                    .andExpect(jsonPath("$.categories[0].id").value(3));

            verify(service).findById(10L);
        }

        @Test
        void returnsNotFoundWhenProductDoesNotExist() throws Exception {
            when(service.findById(404L))
                    .thenThrow(new ResourceNotFoundException("Produto não encontrado"));

            mockMvc.perform(get("/products/404").with(jwt()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/products/10"))
                    .andExpect(status().isUnauthorized());

            verify(service, never()).findById(any());
        }
    }

    @Nested
    class FindAll {

        @Test
        void returnsPagedProductsForAuthenticatedRequest() throws Exception {
            PageRequest pageable = PageRequest.of(1, 2);
            when(service.findAll(eq("phone"), any()))
                    .thenReturn(new PageImpl<>(
                            List.of(new ProductMinDTO(10L, "Phone", 99.99, "phone.png")),
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
                    .andExpect(jsonPath("$.content[0].price").value(99.99));

            verify(service).findAll(eq("phone"), eq(pageable));
        }

        @Test
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/products"))
                    .andExpect(status().isUnauthorized());

            verify(service, never()).findAll(any(), any());
        }
    }

    @Nested
    class Insert {

        @Test
        void createsProductForAdmin() throws Exception {
            when(service.insert(any(ProductDTO.class))).thenReturn(productDTO());

            mockMvc.perform(post("/products")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/products/10"))
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.name").value("Product name"));

            verify(service).insert(any(ProductDTO.class));
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
                    .andExpect(status().isBadRequest());

            verify(service, never()).insert(any());
        }

        @Test
        void rejectsUnauthenticatedRequest() throws Exception {
            mockMvc.perform(post("/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isUnauthorized());

            verify(service, never()).insert(any());
        }

        @Test
        void rejectsNonAdminRequest() throws Exception {
            mockMvc.perform(post("/products")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isForbidden());

            verify(service, never()).insert(any());
        }
    }

    @Nested
    class Update {

        @Test
        void updatesProductForAdmin() throws Exception {
            when(service.update(eq(10L), any(ProductDTO.class))).thenReturn(productDTO());

            mockMvc.perform(put("/products/10")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.name").value("Product name"));

            verify(service).update(eq(10L), any(ProductDTO.class));
        }

        @Test
        void rejectsUnauthenticatedRequest() throws Exception {
            mockMvc.perform(put("/products/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isUnauthorized());

            verify(service, never()).update(any(), any());
        }

        @Test
        void rejectsNonAdminRequest() throws Exception {
            mockMvc.perform(put("/products/10")
                            .with(jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validProductJson()))
                    .andExpect(status().isForbidden());

            verify(service, never()).update(any(), any());
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesProductForAdmin() throws Exception {
            mockMvc.perform(delete("/products/10").with(adminJwt()))
                    .andExpect(status().isNoContent());

            verify(service).deleteById(10L);
        }

        @Test
        void rejectsUnauthenticatedRequest() throws Exception {
            mockMvc.perform(delete("/products/10"))
                    .andExpect(status().isUnauthorized());

            verify(service, never()).deleteById(any());
        }

        @Test
        void rejectsNonAdminRequest() throws Exception {
            mockMvc.perform(delete("/products/10").with(jwt()))
                    .andExpect(status().isForbidden());

            verify(service, never()).deleteById(any());
        }
    }
}
