package io.wlailson.github.e_commerce_catalog_service.service;

import io.wlailson.github.e_commerce_catalog_service.domain.Category;
import io.wlailson.github.e_commerce_catalog_service.domain.Product;
import io.wlailson.github.e_commerce_catalog_service.domain.ReservationStatus;
import io.wlailson.github.e_commerce_catalog_service.domain.StockReservation;
import io.wlailson.github.e_commerce_catalog_service.dto.CategoryDTO;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductDTO;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductMinDTO;
import io.wlailson.github.e_commerce_catalog_service.exceptions.DatabaseException;
import io.wlailson.github.e_commerce_catalog_service.exceptions.ResourceNotFoundException;
import io.wlailson.github.e_commerce_catalog_service.message.OrderCreateMessage;
import io.wlailson.github.e_commerce_catalog_service.message.OrderEvent;
import io.wlailson.github.e_commerce_catalog_service.message.OrderItemCreateMessage;
import io.wlailson.github.e_commerce_catalog_service.repository.ProductRepository;
import io.wlailson.github.e_commerce_catalog_service.repository.StockReservationRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Mock
    private StockReservationRepository stockRepository;

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
                new BigDecimal("25.50"),
                "product.png",
                12,
                null,
                List.of(new CategoryDTO(category.getId(), category.getName()))
        );
    }

    private Product product() {
        Product product = new Product(
                10L,
                "Product name",
                "A sufficiently long product description",
                new BigDecimal("25.50"),
                "product.png",
                12,
                2,
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

            ProductDTO result = service.findProductById(10L);

            assertEquals(10L, result.id());
            assertEquals("Product name", result.name());
            assertEquals(new BigDecimal("25.50"), result.price());
            assertEquals(12, result.stock());
            assertEquals(2, result.reservedStock());
            assertEquals(List.of(new CategoryDTO(3L, "Category")), result.categories());
            verify(repository).findById(10L);
        }

        @Test
        void returnsOutOfStockProductInPublicLookup() {
            Product outOfStockProduct = product();
            outOfStockProduct.setStock(0);
            when(repository.findById(10L)).thenReturn(Optional.of(outOfStockProduct));

            ProductDTO result = service.findProductById(10L);

            assertEquals(0, result.stock());
            verify(repository).findById(10L);
        }

        @Test
        void throwsNotFoundWhenProductDoesNotExist() {
            when(repository.findById(404L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.findProductById(404L));
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

            Page<ProductMinDTO> result = service.findAllProductsByName("phone", pageable);

            assertEquals(3, result.getTotalElements());
            assertEquals(pageable, result.getPageable());
            assertEquals(
                    new ProductMinDTO(10L, "Product name", new BigDecimal("25.50"), "product.png", 12, 2),
                    result.getContent().getFirst()
            );
            verify(repository).searchByName("phone", pageable);
        }

        @Test
        void includesOutOfStockProductsInPublicSearch() {
            PageRequest pageable = PageRequest.of(0, 10);
            Product outOfStockProduct = product();
            outOfStockProduct.setStock(0);
            Page<Product> products = new PageImpl<>(List.of(outOfStockProduct), pageable, 1);
            when(repository.searchByName("phone", pageable)).thenReturn(products);

            Page<ProductMinDTO> result = service.findAllProductsByName("phone", pageable);

            assertEquals(1, result.getTotalElements());
            assertEquals(0, result.getContent().getFirst().stock());
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

            ProductDTO result = service.insertProduct(productDTO());

            ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
            verify(repository).save(productCaptor.capture());
            Product saved = productCaptor.getValue();
            assertEquals(10L, saved.getId());
            assertEquals("Product name", saved.getName());
            assertEquals("A sufficiently long product description", saved.getDescription());
            assertEquals(new BigDecimal("25.50"), saved.getPrice());
            assertEquals(12, saved.getStock());
            assertEquals(0, saved.getReservedStock());
            assertEquals("product.png", saved.getImgUrl());
            assertTrue(saved.getCategories().contains(category));
            assertEquals(10L, result.id());
            assertEquals(0, result.reservedStock());
            assertEquals(List.of(new CategoryDTO(3L, "Category")), result.categories());
            verify(entityManager).find(Category.class, 3L);
        }

        @Test
        void throwsNotFoundAndDoesNotSaveWhenCategoryDoesNotExist() {
            when(entityManager.find(Category.class, 3L)).thenReturn(null);

            assertThrows(ResourceNotFoundException.class, () -> service.insertProduct(productDTO()));

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
            when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
            when(entityManager.find(Category.class, 3L)).thenReturn(category);
            when(repository.save(existing)).thenReturn(existing);

            ProductDTO result = service.updateProduct(10L, productDTO());

            assertEquals("Product name", existing.getName());
            assertEquals("A sufficiently long product description", existing.getDescription());
            assertEquals(new BigDecimal("25.50"), existing.getPrice());
            assertEquals(12, existing.getStock());
            assertEquals(2, existing.getReservedStock());
            assertEquals("product.png", existing.getImgUrl());
            assertEquals(1, existing.getCategories().size());
            assertTrue(existing.getCategories().contains(category));
            assertEquals(List.of(new CategoryDTO(3L, "Category")), result.categories());
            verify(repository).save(existing);
        }

        @Test
        void rejectsStockBelowExistingReservations() {
            Product existing = product();
            when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
            ProductDTO request = new ProductDTO(
                    null,
                    "Product name",
                    "A sufficiently long product description",
                    new BigDecimal("25.50"),
                    "product.png",
                    1,
                    null,
                    List.of(new CategoryDTO(category.getId(), category.getName()))
            );

            assertThrows(IllegalArgumentException.class, () -> service.updateProduct(10L, request));

            assertEquals(12, existing.getStock());
            verify(repository, never()).save(any());
            verifyNoInteractions(entityManager);
        }

        @Test
        void throwsNotFoundWhenProductDoesNotExist() {
            when(repository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.updateProduct(404L, productDTO()));

            verifyNoInteractions(entityManager);
            verify(repository, never()).save(any());
        }

        @Test
        void throwsNotFoundWhenCategoryDoesNotExist() {
            when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(product()));
            when(entityManager.find(Category.class, 3L)).thenReturn(null);

            assertThrows(ResourceNotFoundException.class, () -> service.updateProduct(10L, productDTO()));

            verify(repository, never()).save(any());
        }
    }

    @Nested
    class AddStock {

        @Test
        void addsQuantityToExistingStock() {
            Product existing = product();
            when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);

            ProductDTO result = service.addStock(10L, 5);

            assertEquals(17, result.stock());
            assertEquals(17, existing.getStock());
            verify(repository).save(existing);
        }

        @Test
        void rejectsZeroAndNegativeQuantities() {
            assertThrows(IllegalArgumentException.class, () -> service.addStock(10L, 0));
            assertThrows(IllegalArgumentException.class, () -> service.addStock(10L, -1));

            verifyNoInteractions(repository);
        }

        @Test
        void throwsNotFoundWhenProductDoesNotExist() {
            when(repository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.addStock(404L, 5));

            verify(repository, never()).save(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesExistingProductAndFlushesChanges() {
            Product product = product();
            when(repository.findById(10L)).thenReturn(Optional.of(product));

            service.deleteProductById(10L);

            verify(repository).delete(product);
            verify(repository).flush();
        }

        @Test
        void throwsNotFoundWhenProductDoesNotExist() {
            when(repository.findById(404L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.deleteProductById(404L));

            verify(repository, never()).delete(any(Product.class));
            verify(repository, never()).flush();
        }

        @Test
        void translatesIntegrityViolationToDatabaseException() {
            Product product = product();
            when(repository.findById(10L)).thenReturn(Optional.of(product));
            org.mockito.Mockito.doThrow(new DataIntegrityViolationException("constraint violation"))
                    .when(repository).flush();

            DatabaseException exception = assertThrows(DatabaseException.class, () -> service.deleteProductById(10L));

            assertEquals("Falha de integridade referencial", exception.getMessage());
            verify(repository).delete(product);
            verify(repository).flush();
        }

    }

    @Nested
    class StockReservations {

        @Test
        void aggregatesRepeatedProductLinesBeforeReserving() {
            Product product = product();
            List<StockReservation> savedReservations = new ArrayList<>();
            OrderCreateMessage message = orderMessage(
                    50L,
                    Instant.now().plusSeconds(600),
                    Set.of(
                            new OrderItemCreateMessage(10L, 2),
                            new OrderItemCreateMessage(10L, 3)
                    )
            );
            when(repository.searchAllByIdsForUpdate(List.of(10L))).thenReturn(List.of(product));
            when(stockRepository.saveAll(any())).thenAnswer(invocation -> {
                Iterable<StockReservation> reservations = invocation.getArgument(0);
                reservations.forEach(savedReservations::add);
                return savedReservations;
            });

            service.processOrderCreated(message);

            assertEquals(7, product.getReservedStock());
            assertEquals(1, savedReservations.size());
            assertEquals(5, savedReservations.getFirst().getQuantity());
        }

        @Test
        void rejectsInsufficientAggregatedQuantityWithoutSavingReservations() {
            Product product = product();
            OrderCreateMessage message = orderMessage(
                    51L,
                    Instant.now().plusSeconds(600),
                    Set.of(
                            new OrderItemCreateMessage(10L, 6),
                            new OrderItemCreateMessage(10L, 5)
                    )
            );
            when(repository.searchAllByIdsForUpdate(List.of(10L))).thenReturn(List.of(product));

            assertThrows(IllegalArgumentException.class, () -> service.processOrderCreated(message));

            assertEquals(2, product.getReservedStock());
            verify(stockRepository, never()).saveAll(any());
        }

        @Test
        void ignoresRedeliveredOrderCreation() {
            when(stockRepository.existsByOrderId(52L)).thenReturn(true);

            service.processOrderCreated(orderMessage(
                    52L,
                    Instant.now().plusSeconds(600),
                    Set.of(new OrderItemCreateMessage(10L, 1))
            ));

            verifyNoInteractions(repository);
            verify(stockRepository, never()).saveAll(any());
        }

        @Test
        void rejectsNonPositiveItemQuantity() {
            assertThrows(IllegalArgumentException.class, () -> service.processOrderCreated(orderMessage(
                    53L,
                    Instant.now().plusSeconds(600),
                    Set.of(new OrderItemCreateMessage(10L, 0))
            )));

            verifyNoInteractions(repository, stockRepository);
        }

        @Test
        void confirmsUnexpiredReservationAndDecrementsStockAndReservedStock() {
            Product product = product();
            StockReservation reservation = reservation(60L, product, Instant.now().plusSeconds(600));
            when(stockRepository.findByOrderIdAndStatus(60L, ReservationStatus.RESERVED))
                    .thenReturn(List.of(reservation));
            when(repository.searchAllByIdsForUpdate(List.of(10L))).thenReturn(List.of(product));

            service.confirmReservations(60L);

            assertEquals(10, product.getStock());
            assertEquals(0, product.getReservedStock());
            assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        }

        @Test
        void releasesInsteadOfConfirmingExpiredReservation() {
            Product product = product();
            StockReservation reservation = reservation(61L, product, Instant.now().minusSeconds(1));
            when(stockRepository.findByOrderIdAndStatus(61L, ReservationStatus.RESERVED))
                    .thenReturn(List.of(reservation));
            when(repository.searchAllByIdsForUpdate(List.of(10L))).thenReturn(List.of(product));

            service.confirmReservations(61L);

            assertEquals(12, product.getStock());
            assertEquals(0, product.getReservedStock());
            assertEquals(ReservationStatus.RELEASED, reservation.getStatus());
        }

        @Test
        void releasesReservationOnlyOnceWhenRepeated() {
            Product product = product();
            StockReservation reservation = reservation(62L, product, Instant.now().plusSeconds(600));
            when(stockRepository.findByOrderIdAndStatus(62L, ReservationStatus.RESERVED))
                    .thenReturn(List.of(reservation), List.of(reservation), List.of());
            when(repository.searchAllByIdsForUpdate(List.of(10L))).thenReturn(List.of(product));

            service.releaseReservation(62L);
            service.releaseReservation(62L);

            assertEquals(0, product.getReservedStock());
            assertEquals(ReservationStatus.RELEASED, reservation.getStatus());
            verify(repository).searchAllByIdsForUpdate(List.of(10L));
        }
    }

    private OrderCreateMessage orderMessage(Long orderId, Instant expiresAt, Set<OrderItemCreateMessage> items) {
        return new OrderCreateMessage(orderId, OrderEvent.CREATE, Instant.now(), expiresAt, items);
    }

    private StockReservation reservation(Long orderId, Product product, Instant expiresAt) {
        StockReservation reservation = new StockReservation();
        reservation.setOrderId(orderId);
        reservation.setProduct(product);
        reservation.setQuantity(2);
        reservation.setCreatedAt(Instant.now());
        reservation.setExpiresAt(expiresAt);
        reservation.setStatus(ReservationStatus.RESERVED);
        return reservation;
    }
}
