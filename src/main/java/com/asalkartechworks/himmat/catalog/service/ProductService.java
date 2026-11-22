package com.asalkartechworks.himmat.catalog.service;

import com.asalkartechworks.himmat.catalog.dto.CreateProductRequest;
import com.asalkartechworks.himmat.catalog.dto.ProductDto;
import com.asalkartechworks.himmat.catalog.dto.UpdateProductRequest;
import com.asalkartechworks.himmat.catalog.model.Brand;
import com.asalkartechworks.himmat.catalog.model.Category;
import com.asalkartechworks.himmat.catalog.model.Product;
import com.asalkartechworks.himmat.catalog.model.Subcategory;
import com.asalkartechworks.himmat.catalog.repository.BrandRepository;
import com.asalkartechworks.himmat.catalog.repository.ProductRepository;
import com.asalkartechworks.himmat.catalog.repository.SubcategoryRepository;
import com.asalkartechworks.himmat.common.errors.DuplicateResourceException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.StaleVersionException;
import com.asalkartechworks.himmat.common.errors.ValidationException;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "isActive", "createdAt", "updatedAt"
    );

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public Page<ProductDto> getProducts(Long brandId, Long categoryId, Long subcategoryId, String search, Boolean isActive, Pageable pageable) {
        Pageable sanitized = sanitizePageable(pageable);
        return productRepository.searchProducts(brandId, categoryId, subcategoryId, search, isActive, sanitized).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", String.valueOf(id)));
        return toDto(product);
    }

    @Transactional
    public ProductDto createProduct(CreateProductRequest request, Long currentActorId) {
        Brand brand = brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand", String.valueOf(request.getBrandId())));

        if (!brand.isActive()) {
            throw new ValidationException("Cannot assign product to inactive brand: " + brand.getName());
        }

        Subcategory subcategory = subcategoryRepository.findById(request.getSubcategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory", String.valueOf(request.getSubcategoryId())));

        if (!subcategory.isActive()) {
            throw new ValidationException("Cannot assign product to inactive subcategory: " + subcategory.getName());
        }

        String trimmedName = request.getName().trim();
        if (productRepository.existsByBrandIdAndSubcategoryIdAndNameIgnoreCase(brand.getId(), subcategory.getId(), trimmedName)) {
            throw new DuplicateResourceException(
                    "Product with name '" + trimmedName + "' already exists for brand '" + brand.getName() + "' and subcategory '" + subcategory.getName() + "'."
            );
        }

        Instant now = businessClock.now();
        Product product = Product.builder()
                .brand(brand)
                .subcategory(subcategory)
                .name(trimmedName)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(currentActorId)
                .updatedBy(currentActorId)
                .build();

        Product saved = productRepository.save(product);
        log.info("Created product ID: {} with name: {}", saved.getId(), saved.getName());
        return toDto(saved);
    }

    @Transactional
    public ProductDto updateProduct(Long id, UpdateProductRequest request, Long currentActorId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", String.valueOf(id)));

        if (!product.getVersion().equals(request.getExpectedVersion())) {
            throw new StaleVersionException("The record changed. Reload before editing.", product.getVersion());
        }

        Long targetBrandId = product.getBrand().getId();
        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand", String.valueOf(request.getBrandId())));
            if (!brand.isActive()) {
                throw new ValidationException("Cannot assign product to inactive brand: " + brand.getName());
            }
            product.setBrand(brand);
            targetBrandId = brand.getId();
        }

        Long targetSubcatId = product.getSubcategory().getId();
        if (request.getSubcategoryId() != null) {
            Subcategory subcategory = subcategoryRepository.findById(request.getSubcategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subcategory", String.valueOf(request.getSubcategoryId())));
            if (!subcategory.isActive()) {
                throw new ValidationException("Cannot assign product to inactive subcategory: " + subcategory.getName());
            }
            product.setSubcategory(subcategory);
            targetSubcatId = subcategory.getId();
        }

        String targetName = product.getName();
        if (request.getName() != null && !request.getName().isBlank()) {
            targetName = request.getName().trim();
            product.setName(targetName);
        }

        if (productRepository.existsByBrandIdAndSubcategoryIdAndNameIgnoreCaseAndIdNot(targetBrandId, targetSubcatId, targetName, id)) {
            throw new DuplicateResourceException(
                    "Product with name '" + targetName + "' already exists for the specified brand and subcategory."
            );
        }

        if (request.getDescription() != null) {
            product.setDescription(request.getDescription().trim());
        }

        if (request.getIsActive() != null) {
            product.setActive(request.getIsActive());
        }

        product.setUpdatedAt(businessClock.now());
        product.setUpdatedBy(currentActorId);

        Product saved = productRepository.save(product);
        log.info("Updated product ID: {} version: {}", saved.getId(), saved.getVersion());
        return toDto(saved);
    }

    public ProductDto toDto(Product product) {
        Subcategory sub = product.getSubcategory();
        Category cat = sub != null ? sub.getCategory() : null;

        return ProductDto.builder()
                .id(product.getId())
                .brandId(product.getBrand().getId())
                .brandName(product.getBrand().getName())
                .subcategoryId(sub != null ? sub.getId() : null)
                .subcategoryName(sub != null ? sub.getName() : null)
                .categoryId(cat != null ? cat.getId() : null)
                .categoryName(cat != null ? cat.getName() : null)
                .name(product.getName())
                .description(product.getDescription())
                .isActive(product.isActive())
                .version(product.getVersion())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort()) {
                if (ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    sort = Sort.by(order.getDirection(), order.getProperty()).and(Sort.by(Sort.Direction.ASC, "id"));
                    break;
                }
            }
        }
        return PageRequest.of(page, size, sort);
    }
}
