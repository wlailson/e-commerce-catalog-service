package io.wlailson.github.e_commerce_catalog_service.service;

import io.wlailson.github.e_commerce_catalog_service.domain.Category;
import io.wlailson.github.e_commerce_catalog_service.domain.Product;
import io.wlailson.github.e_commerce_catalog_service.dto.CategoryDTO;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductDTO;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductMinDTO;
import io.wlailson.github.e_commerce_catalog_service.exceptions.DatabaseException;
import io.wlailson.github.e_commerce_catalog_service.exceptions.ResourceNotFoundException;
import io.wlailson.github.e_commerce_catalog_service.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ProductService service;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category(3L, "Category", new HashSet<>());
    }

    private ProductDTO productDTO() {
        return new ProductDTO(
                null,
                "Product name",
                "A sufficiently long product description",
                25.50,
                "product.png",
                List.of(new CategoryDTO(category.getId(), category.getName()))
        );
    }

    private Product product() {
        Product product = new Product(
                10L,
                "Product name",
                "A sufficiently long product description",
                25.50,
                "product.png",
                new HashSet<>()
        );
        product.getCategories().add(category);
        return product;
    }

    @Nested
    class FindById {

        @Test
        void returnsProductDTOWithCategories() {
            when(repository.findById(10L)).thenReturn(Optional.of(product()));

            ProductDTO result = service.findById(10L);

            assertEquals(10L, result.id());
            assertEquals("Product name", result.name());
            assertEquals(25.50, result.price());
            assertEquals(List.of(new CategoryDTO(3L, "Category")), result.categories());
            verify(repository).findById(10L);
        }

        @Test
        void throwsNotFoundWhenProductDoesNotExist() {
            when(repository.findById(404L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.findById(404L));
        }
    }

    @Nested
    class FindAll {

        @Test
        void returnsPageOfMinimumProductDTOs() {
            PageRequest pageable = PageRequest.of(1, 2);
            Page<Product> products = new PageImpl<>(
                    List.of(product()),
                    pageable,
                    3
            );
            when(repository.searchByName("phone", pageable)).thenReturn(products);

            Page<ProductMinDTO> result = service.findAll("phone", pageable);

            assertEquals(3, result.getTotalElements());
            assertEquals(pageable, result.getPageable());
            assertEquals(
                    new ProductMinDTO(10L, "Product name", 25.50, "product.png"),
                    result.getContent().getFirst()
            );
            verify(repository).searchByName("phone", pageable);
        }
    }

    @Nested
    class Insert {

        @Test
        void savesProductWithRequestFieldsAndResolvedCategories() {
            when(entityManager.find(Category.class, 3L)).thenReturn(category);
            when(repository.save(any(Product.class))).thenAnswer(invocation -> {
                Product saved = invocation.getArgument(0);
                saved.setId(10L);
                return saved;
            });

            ProductDTO result = service.insert(productDTO());

            ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
            verify(repository).save(productCaptor.capture());
            Product saved = productCaptor.getValue();
            assertEquals(10L, saved.getId());
            assertEquals("Product name", saved.getName());
            assertEquals("A sufficiently long product description", saved.getDescription());
            assertEquals(25.50, saved.getPrice());
            assertEquals("product.png", saved.getImgUrl());
            assertTrue(saved.getCategories().contains(category));
            assertEquals(10L, result.id());
            assertEquals(List.of(new CategoryDTO(3L, "Category")), result.categories());
            verify(entityManager).find(Category.class, 3L);
        }

        @Test
        void throwsNotFoundAndDoesNotSaveWhenCategoryDoesNotExist() {
            when(entityManager.find(Category.class, 3L)).thenReturn(null);

            assertThrows(ResourceNotFoundException.class, () -> service.insert(productDTO()));

            verify(repository, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void updatesProductFieldsAndReplacesCategories() {
            Product existing = product();
            Category previousCategory = new Category(8L, "Old category", new HashSet<>());
            existing.getCategories().clear();
            existing.getCategories().add(previousCategory);
            when(repository.findById(10L)).thenReturn(Optional.of(existing));
            when(entityManager.find(Category.class, 3L)).thenReturn(category);
            when(repository.save(existing)).thenReturn(existing);

            ProductDTO result = service.update(10L, productDTO());

            assertEquals("Product name", existing.getName());
            assertEquals("A sufficiently long product description", existing.getDescription());
            assertEquals(25.50, existing.getPrice());
            assertEquals("product.png", existing.getImgUrl());
            assertEquals(1, existing.getCategories().size());
            assertTrue(existing.getCategories().contains(category));
            assertEquals(List.of(new CategoryDTO(3L, "Category")), result.categories());
            verify(repository).save(existing);
        }

        @Test
        void throwsNotFoundWhenProductDoesNotExist() {
            when(repository.findById(404L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.update(404L, productDTO()));

            verifyNoInteractions(entityManager);
            verify(repository, never()).save(any());
        }

        @Test
        void throwsNotFoundWhenCategoryDoesNotExist() {
            when(repository.findById(10L)).thenReturn(Optional.of(product()));
            when(entityManager.find(Category.class, 3L)).thenReturn(null);

            assertThrows(ResourceNotFoundException.class, () -> service.update(10L, productDTO()));

            verify(repository, never()).save(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesExistingProductAndFlushesChanges() {
            Product product = product();
            when(repository.findById(10L)).thenReturn(Optional.of(product));

            service.deleteById(10L);

            verify(repository).delete(product);
            verify(repository).flush();
        }

        @Test
        void throwsNotFoundWhenProductDoesNotExist() {
            when(repository.findById(404L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.deleteById(404L));

            verify(repository, never()).delete(any(Product.class));
            verify(repository, never()).flush();
        }

        @Test
        void translatesIntegrityViolationToDatabaseException() {
            Product product = product();
            when(repository.findById(10L)).thenReturn(Optional.of(product));
            org.mockito.Mockito.doThrow(new DataIntegrityViolationException("constraint violation"))
                    .when(repository).flush();

            DatabaseException exception = assertThrows(DatabaseException.class, () -> service.deleteById(10L));

            assertEquals("Falha de integridade referencial", exception.getMessage());
            verify(repository).delete(product);
            verify(repository).flush();
        }
    }
}
