package io.wlailson.github.e_commerce_catalog_service.service;

import io.wlailson.github.e_commerce_catalog_service.domain.Category;
import io.wlailson.github.e_commerce_catalog_service.domain.Product;
import io.wlailson.github.e_commerce_catalog_service.domain.ReservationStatus;
import io.wlailson.github.e_commerce_catalog_service.domain.StockReservation;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductDTO;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductMinDTO;
import io.wlailson.github.e_commerce_catalog_service.exceptions.DatabaseException;
import io.wlailson.github.e_commerce_catalog_service.exceptions.ResourceNotFoundException;
import io.wlailson.github.e_commerce_catalog_service.message.OrderCreateMessage;
import io.wlailson.github.e_commerce_catalog_service.message.OrderItemCreateMessage;
import io.wlailson.github.e_commerce_catalog_service.repository.ProductRepository;
import io.wlailson.github.e_commerce_catalog_service.repository.StockReservationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository repository;
    private final EntityManager entityManager;
    private final StockReservationRepository stockRepository;

    @Transactional(readOnly = true)
    public ProductDTO findProductById(Long productId) {
        Product product = loadEntity(productId);
        return new ProductDTO(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductMinDTO> findAllProductsByName(String name, Pageable pageable) {
        Page<Product> result = repository.searchByName(name, pageable);
        return result.map(this::toProductMinDTO);
    }

    @Transactional
    public ProductDTO insertProduct(ProductDTO request) {
        Product product = new Product();
        copyDtoToEntity(request, product);
        product = repository.save(product);
        return new ProductDTO(product);
    }

    @Transactional
    public ProductDTO updateProduct(Long productId, ProductDTO request) {
        Product entity = loadEntityForUpdate(productId);
        copyDtoToEntity(request, entity);
        entity = repository.save(entity);
        return new ProductDTO(entity);
    }

    @Transactional
    public ProductDTO addStock(Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("A quantidade a acrescentar deve ser maior que zero");
        }
        Product product = loadEntityForUpdate(productId);
        product.setStock(Math.addExact(product.getStock(), quantity));
        return new ProductDTO(repository.save(product));
    }

    @Transactional
    public void deleteProductById(Long productId) {
        try {
            Product product = loadEntity(productId);
            repository.delete(product);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new DatabaseException("Falha de integridade referencial");
        }
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> findAllByIds(List<Long> productIds) {
        List<Product> products = repository.searchAllByIds(productIds);
        return products.stream().map(ProductDTO::new).toList();
    }

    @Transactional
    public void processOrderCreated(OrderCreateMessage message) {
        Map<Long, Integer> quantitiesByProduct = aggregateQuantities(message);
        if (stockRepository.existsByOrderId(message.orderId())) {
            return;
        }

        Map<Long, Product> productsById = lockProductsForReservation(quantitiesByProduct);

        if (stockRepository.existsByOrderId(message.orderId())) {
            return;
        }

        if (isReservationExpired(message)) {
            return;
        }

        validateAvailableStock(quantitiesByProduct, productsById);
        List<StockReservation> reservations = new ArrayList<>(quantitiesByProduct.size());
        for (Map.Entry<Long, Integer> entry : quantitiesByProduct.entrySet()) {
            Product product = productsById.get(entry.getKey());
            int quantity = entry.getValue();
            reservations.add(createReservation(message, product, quantity));
        }
        stockRepository.saveAll(reservations);
    }

    @Transactional
    public void confirmReservations(Long orderId) {
        List<StockReservation> candidates = findReservationsToTransition(orderId);
        if (candidates.isEmpty()) {
            return;
        }
        lockProductsForReservations(candidates);
        List<StockReservation> reservations = stockRepository.findByOrderIdAndStatus(
                orderId,
                ReservationStatus.RESERVED
        );
        Instant now = Instant.now();
        for (StockReservation reservation : reservations) {
            confirmReservation(reservation, orderId, now);
        }
    }

    @Transactional
    public void releaseReservation(Long orderId) {
        List<StockReservation> candidates = findReservationsToTransition(orderId);
        if (candidates.isEmpty()) {
            return;
        }
        lockProductsForReservations(candidates);
        stockRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED)
                .forEach(this::releaseReservedQuantity);
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void releaseExpiredReservations() {
        Instant now = Instant.now();
        List<StockReservation> candidates = stockRepository.findExpiredReservations(
                now,
                ReservationStatus.RESERVED
        );
        if (candidates.isEmpty()) {
            return;
        }
        lockProductsForReservations(candidates);
        stockRepository.findExpiredReservations(now, ReservationStatus.RESERVED)
                .forEach(this::releaseReservedQuantity);
    }

    private ProductMinDTO toProductMinDTO(Product product) {
        return new ProductMinDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getImgUrl(),
                product.getStock(),
                product.getReservedStock());
    }

    private Product loadEntity(Long productId) {
        return repository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Produto de id " + productId + " não encontrado"
                ));
    }

    private Product loadEntityForUpdate(Long productId) {
        return repository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Produto de id " + productId + " não encontrado"
                ));
    }

    private Map<Long, Product> lockProductsForReservation(Map<Long, Integer> quantitiesByProduct) {
        List<Long> productIds = List.copyOf(quantitiesByProduct.keySet());
        List<Product> products = repository.searchAllByIdsForUpdate(productIds);
        Map<Long, Product> productsById = new TreeMap<>();
        products.forEach(product -> productsById.put(product.getId(), product));

        for (Long productId : productIds) {
            if (!productsById.containsKey(productId)) {
                throw new ResourceNotFoundException("Produto de id " + productId + " não encontrado");
            }
        }
        return productsById;
    }

    private boolean isReservationExpired(OrderCreateMessage message) {
        if (message.expiresAt().isAfter(Instant.now())) {
            return false;
        }
        log.warn("Ignoring expired order reservation request: orderId={}", message.orderId());
        return true;
    }

    private void validateAvailableStock(
            Map<Long, Integer> quantitiesByProduct,
            Map<Long, Product> productsById
    ) {
        for (Map.Entry<Long, Integer> entry : quantitiesByProduct.entrySet()) {
            Product product = productsById.get(entry.getKey());
            validateAvailableStock(product, entry.getValue());
        }
    }

    private StockReservation createReservation(OrderCreateMessage message, Product product, int quantity) {
        product.setReservedStock(product.getReservedStock() + quantity);

        StockReservation reservation = new StockReservation();
        reservation.setOrderId(message.orderId());
        reservation.setProduct(product);
        reservation.setQuantity(quantity);
        reservation.setCreatedAt(message.occurredAt());
        reservation.setExpiresAt(message.expiresAt());
        reservation.setStatus(ReservationStatus.RESERVED);
        return reservation;
    }

    private Map<Long, Integer> aggregateQuantities(OrderCreateMessage message) {
        if (message == null || message.orderId() == null || message.orderId() <= 0) {
            throw new IllegalArgumentException("Pedido inválido");
        }
        if (message.occurredAt() == null || message.expiresAt() == null
                || !message.expiresAt().isAfter(message.occurredAt())) {
            throw new IllegalArgumentException("Período de reserva inválido");
        }
        if (message.items() == null || message.items().isEmpty()) {
            throw new IllegalArgumentException("O pedido deve conter itens");
        }

        Map<Long, Integer> quantitiesByProduct = new TreeMap<>();
        for (OrderItemCreateMessage item : message.items()) {
            if (item == null || item.productId() == null || item.productId() <= 0
                    || item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Item de pedido inválido");
            }
            try {
                quantitiesByProduct.merge(item.productId(), item.quantity(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "Quantidade total inválida para o produto: " + item.productId(),
                        exception
                );
            }
        }
        return quantitiesByProduct;
    }

    private void validateAvailableStock(Product product, int quantity) {
        Integer stock = product.getStock();
        Integer reservedStock = product.getReservedStock();
        if (stock == null || stock < 0
                || reservedStock == null || reservedStock < 0 || reservedStock > stock) {
            throw new IllegalStateException("Estoque inconsistente para o produto: " + product.getId());
        }
        if (stock - reservedStock < quantity) {
            throw new IllegalArgumentException(
                    "Estoque insuficiente para o produto: " + product.getId()
            );
        }
    }

    private List<StockReservation> findReservationsToTransition(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Identificador do pedido inválido");
        }
        return stockRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);
    }

    private void confirmReservation(StockReservation reservation, Long orderId, Instant now) {
        Product product = reservation.getProduct();
        validateReservationForTransition(reservation);
        if (reservation.getExpiresAt() == null || !reservation.getExpiresAt().isAfter(now)) {
            releaseReservedQuantity(reservation);
            log.warn(
                    "Ignoring confirmation for expired stock reservation: orderId={}, productId={}",
                    orderId,
                    product.getId()
            );
            return;
        }

        product.setStock(product.getStock() - reservation.getQuantity());
        decreaseReservedStock(product, reservation.getQuantity());
        reservation.setStatus(ReservationStatus.CONFIRMED);
    }

    private void lockProductsForReservations(List<StockReservation> reservations) {
        List<Long> productIds = reservations.stream()
                .map(reservation -> reservation.getProduct().getId())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<Product> products = repository.searchAllByIdsForUpdate(productIds);
        if (products.size() != productIds.size()) {
            throw new IllegalStateException("Produto associado à reserva não foi encontrado");
        }
        products.forEach(product -> entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE));
    }

    private void releaseReservedQuantity(StockReservation reservation) {
        validateReservationForTransition(reservation);
        decreaseReservedStock(reservation.getProduct(), reservation.getQuantity());
        reservation.setStatus(ReservationStatus.RELEASED);
    }

    private void validateReservationForTransition(StockReservation reservation) {
        Product product = reservation.getProduct();
        Integer quantity = reservation.getQuantity();
        Integer stock = product.getStock();
        Integer reservedStock = product.getReservedStock();
        if (quantity == null || quantity <= 0 || stock == null || stock < 0
                || reservedStock == null || reservedStock < quantity || reservedStock > stock) {
            throw new IllegalStateException(
                    "Estoque inconsistente para o produto: " + product.getId()
            );
        }
    }

    private void decreaseReservedStock(Product product, int quantity) {
        Integer reservedStock = product.getReservedStock();
        if (reservedStock == null || reservedStock < quantity) {
            throw new IllegalStateException(
                    "Estoque reservado inconsistente para o produto: " + product.getId()
            );
        }
        product.setReservedStock(reservedStock - quantity);
    }

    private void copyDtoToEntity(ProductDTO request, Product entity) {
        if (request.stock() == null || request.stock() < 0) {
            throw new IllegalArgumentException("O estoque não pode ser negativo");
        }
        Integer reservedStock = entity.getReservedStock();
        Integer currentStock = entity.getStock();
        if (reservedStock == null || reservedStock < 0
                || (entity.getId() != null
                    && (currentStock == null || currentStock < 0 || reservedStock > currentStock))) {
            throw new IllegalStateException("Estoque reservado inconsistente para o produto: " + entity.getId());
        }
        if (request.stock() < reservedStock) {
            throw new IllegalArgumentException(
                    "O estoque não pode ser menor que a quantidade reservada"
            );
        }
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setImgUrl(request.imgUrl());
        entity.setPrice(request.price());
        entity.setStock(request.stock());
        entity.getCategories().clear();
        request.categories().forEach(categoryDTO -> {
            Category category = entityManager.find(Category.class, categoryDTO.id());
            if (category == null) {
                throw new ResourceNotFoundException(
                        "Categoria de id " + categoryDTO.id() + " não encontrada"
                );
            }
            entity.getCategories().add(category);
        });
    }
}
