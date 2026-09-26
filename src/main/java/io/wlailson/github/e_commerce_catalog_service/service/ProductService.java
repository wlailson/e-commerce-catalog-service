package io.wlailson.github.e_commerce_catalog_service.service;

import io.wlailson.github.e_commerce_catalog_service.domain.Category;
import io.wlailson.github.e_commerce_catalog_service.domain.Product;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductDTO;
import io.wlailson.github.e_commerce_catalog_service.dto.ProductMinDTO;
import io.wlailson.github.e_commerce_catalog_service.exceptions.DatabaseException;
import io.wlailson.github.e_commerce_catalog_service.exceptions.ResourceNotFoundException;
import io.wlailson.github.e_commerce_catalog_service.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public ProductDTO findById(Long id) {
        Product product = loadEntity(id);
        return new ProductDTO(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductMinDTO> findAll(String name, Pageable pageable) {
        Page<Product> result = repository.searchByName(name, pageable);
        return result.map(this::toProductMinDTO);
    }

    @Transactional
    public ProductDTO insert(ProductDTO dto) {
        Product product = new Product();
        copyDtoToEntity(dto, product);
        product = repository.save(product);
        return new ProductDTO(product);
    }

    @Transactional
    public ProductDTO update(Long id, ProductDTO dto) {
        Product entity = loadEntity(id);
        copyDtoToEntity(dto, entity);
        entity = repository.save(entity);
        return new ProductDTO(entity);
    }

    @Transactional
    public void delete(Long id) {
        try {
            Product product = loadEntity(id);
            repository.delete(product);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new DatabaseException("Falha de integridade referencial");
        }
    }

    private ProductMinDTO toProductMinDTO(Product product) {
        return new ProductMinDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getImgUrl());
    }

    private Product loadEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto de id " + id + " não encontrado"));
    }

    private void copyDtoToEntity(ProductDTO dto, Product entity) {
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setImgUrl(dto.imgUrl());
        entity.setPrice(dto.price());
        entity.getCategories().clear();
        dto.categories().forEach(x -> {
            Category category = entityManager.find(Category.class, x.id());
            if (category == null) {
                throw new ResourceNotFoundException("Categoria de id " + x.id() + " não encontrada");
            }
            entity.getCategories().add(category);
        });
    }
}
