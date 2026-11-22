package com.asalkartechworks.himmat.catalog.service;

import com.asalkartechworks.himmat.catalog.dto.CategoryDto;
import com.asalkartechworks.himmat.catalog.dto.CreateCategoryRequest;
import com.asalkartechworks.himmat.catalog.dto.UpdateCategoryRequest;
import com.asalkartechworks.himmat.catalog.model.Category;
import com.asalkartechworks.himmat.catalog.repository.CategoryRepository;
import com.asalkartechworks.himmat.common.errors.DuplicateResourceException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.StaleVersionException;
import com.asalkartechworks.himmat.common.money.MoneyPolicy;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "purchaseDiscountPct", "salesDiscountPct", "isActive", "createdAt", "updatedAt"
    );

    private final CategoryRepository categoryRepository;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public Page<CategoryDto> getCategories(String search, Boolean isActive, Pageable pageable) {
        Pageable sanitized = sanitizePageable(pageable);
        return categoryRepository.searchCategories(search, isActive, sanitized).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public CategoryDto getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", String.valueOf(id)));
        return toDto(category);
    }

    @Transactional
    public CategoryDto createCategory(CreateCategoryRequest request, Long currentActorId) {
        String trimmedName = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new DuplicateResourceException("Category with name '" + trimmedName + "' already exists.");
        }

        BigDecimal purchaseDisc = request.getPurchaseDiscountPct() != null
                ? MoneyPolicy.roundPercent(request.getPurchaseDiscountPct())
                : BigDecimal.ZERO.setScale(MoneyPolicy.PERCENT_SCALE, MoneyPolicy.DEFAULT_ROUNDING);

        BigDecimal salesDisc = request.getSalesDiscountPct() != null
                ? MoneyPolicy.roundPercent(request.getSalesDiscountPct())
                : BigDecimal.ZERO.setScale(MoneyPolicy.PERCENT_SCALE, MoneyPolicy.DEFAULT_ROUNDING);

        Instant now = businessClock.now();
        Category category = Category.builder()
                .name(trimmedName)
                .purchaseDiscountPct(purchaseDisc)
                .salesDiscountPct(salesDisc)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(currentActorId)
                .updatedBy(currentActorId)
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Created category ID: {} with name: {}", saved.getId(), saved.getName());
        return toDto(saved);
    }

    @Transactional
    public CategoryDto updateCategory(Long id, UpdateCategoryRequest request, Long currentActorId) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", String.valueOf(id)));

        if (!category.getVersion().equals(request.getExpectedVersion())) {
            throw new StaleVersionException("The record changed. Reload before editing.", category.getVersion());
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            String trimmedName = request.getName().trim();
            if (categoryRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
                throw new DuplicateResourceException("Category with name '" + trimmedName + "' already exists.");
            }
            category.setName(trimmedName);
        }

        if (request.getPurchaseDiscountPct() != null) {
            category.setPurchaseDiscountPct(MoneyPolicy.roundPercent(request.getPurchaseDiscountPct()));
        }

        if (request.getSalesDiscountPct() != null) {
            category.setSalesDiscountPct(MoneyPolicy.roundPercent(request.getSalesDiscountPct()));
        }

        if (request.getIsActive() != null) {
            category.setActive(request.getIsActive());
        }

        category.setUpdatedAt(businessClock.now());
        category.setUpdatedBy(currentActorId);

        Category saved = categoryRepository.save(category);
        log.info("Updated category ID: {} version: {}", saved.getId(), saved.getVersion());
        return toDto(saved);
    }

    public CategoryDto toDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .purchaseDiscountPct(category.getPurchaseDiscountPct())
                .salesDiscountPct(category.getSalesDiscountPct())
                .isActive(category.isActive())
                .version(category.getVersion())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
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
