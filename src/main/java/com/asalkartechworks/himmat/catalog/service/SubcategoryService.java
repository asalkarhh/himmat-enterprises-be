package com.asalkartechworks.himmat.catalog.service;

import com.asalkartechworks.himmat.catalog.dto.CreateSubcategoryRequest;
import com.asalkartechworks.himmat.catalog.dto.SubcategoryDto;
import com.asalkartechworks.himmat.catalog.dto.UpdateSubcategoryRequest;
import com.asalkartechworks.himmat.catalog.model.Category;
import com.asalkartechworks.himmat.catalog.model.Subcategory;
import com.asalkartechworks.himmat.catalog.repository.CategoryRepository;
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
public class SubcategoryService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "isActive", "createdAt", "updatedAt"
    );

    private final SubcategoryRepository subcategoryRepository;
    private final CategoryRepository categoryRepository;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public Page<SubcategoryDto> getSubcategories(Long categoryId, String search, Boolean isActive, Pageable pageable) {
        Pageable sanitized = sanitizePageable(pageable);
        return subcategoryRepository.searchSubcategories(categoryId, search, isActive, sanitized).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public SubcategoryDto getSubcategoryById(Long id) {
        Subcategory subcategory = subcategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory", String.valueOf(id)));
        return toDto(subcategory);
    }

    @Transactional
    public SubcategoryDto createSubcategory(CreateSubcategoryRequest request, Long currentActorId) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", String.valueOf(request.getCategoryId())));

        if (!category.isActive()) {
            throw new ValidationException("Cannot create subcategory under inactive category: " + category.getName());
        }

        String trimmedName = request.getName().trim();
        if (subcategoryRepository.existsByCategoryIdAndNameIgnoreCase(category.getId(), trimmedName)) {
            throw new DuplicateResourceException(
                    "Subcategory with name '" + trimmedName + "' already exists under category '" + category.getName() + "'."
            );
        }

        Instant now = businessClock.now();
        Subcategory subcategory = Subcategory.builder()
                .category(category)
                .name(trimmedName)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(currentActorId)
                .updatedBy(currentActorId)
                .build();

        Subcategory saved = subcategoryRepository.save(subcategory);
        log.info("Created subcategory ID: {} under category ID: {}", saved.getId(), category.getId());
        return toDto(saved);
    }

    @Transactional
    public SubcategoryDto updateSubcategory(Long id, UpdateSubcategoryRequest request, Long currentActorId) {
        Subcategory subcategory = subcategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory", String.valueOf(id)));

        if (!subcategory.getVersion().equals(request.getExpectedVersion())) {
            throw new StaleVersionException("The record changed. Reload before editing.", subcategory.getVersion());
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            String trimmedName = request.getName().trim();
            if (subcategoryRepository.existsByCategoryIdAndNameIgnoreCaseAndIdNot(subcategory.getCategory().getId(), trimmedName, id)) {
                throw new DuplicateResourceException(
                        "Subcategory with name '" + trimmedName + "' already exists under category '" + subcategory.getCategory().getName() + "'."
                );
            }
            subcategory.setName(trimmedName);
        }

        if (request.getIsActive() != null) {
            subcategory.setActive(request.getIsActive());
        }

        subcategory.setUpdatedAt(businessClock.now());
        subcategory.setUpdatedBy(currentActorId);

        Subcategory saved = subcategoryRepository.save(subcategory);
        log.info("Updated subcategory ID: {} version: {}", saved.getId(), saved.getVersion());
        return toDto(saved);
    }

    public SubcategoryDto toDto(Subcategory subcategory) {
        return SubcategoryDto.builder()
                .id(subcategory.getId())
                .categoryId(subcategory.getCategory().getId())
                .categoryName(subcategory.getCategory().getName())
                .name(subcategory.getName())
                .isActive(subcategory.isActive())
                .version(subcategory.getVersion())
                .createdAt(subcategory.getCreatedAt())
                .updatedAt(subcategory.getUpdatedAt())
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
