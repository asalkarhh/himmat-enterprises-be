package com.asalkartechworks.himmat.catalog.service;

import com.asalkartechworks.himmat.catalog.dto.BrandDto;
import com.asalkartechworks.himmat.catalog.dto.CreateBrandRequest;
import com.asalkartechworks.himmat.catalog.dto.UpdateBrandRequest;
import com.asalkartechworks.himmat.catalog.model.Brand;
import com.asalkartechworks.himmat.catalog.repository.BrandRepository;
import com.asalkartechworks.himmat.common.errors.DuplicateResourceException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.StaleVersionException;
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
public class BrandService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "isActive", "createdAt", "updatedAt");

    private final BrandRepository brandRepository;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public Page<BrandDto> getBrands(String search, Boolean isActive, Pageable pageable) {
        Pageable sanitized = sanitizePageable(pageable);
        return brandRepository.searchBrands(search, isActive, sanitized).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public BrandDto getBrandById(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", String.valueOf(id)));
        return toDto(brand);
    }

    @Transactional
    public BrandDto createBrand(CreateBrandRequest request, Long currentActorId) {
        String trimmedName = request.getName().trim();
        if (brandRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new DuplicateResourceException("Brand with name '" + trimmedName + "' already exists.");
        }

        Instant now = businessClock.now();
        Brand brand = Brand.builder()
                .name(trimmedName)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(currentActorId)
                .updatedBy(currentActorId)
                .build();

        Brand saved = brandRepository.save(brand);
        log.info("Created brand ID: {} with name: {}", saved.getId(), saved.getName());
        return toDto(saved);
    }

    @Transactional
    public BrandDto updateBrand(Long id, UpdateBrandRequest request, Long currentActorId) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", String.valueOf(id)));

        if (!brand.getVersion().equals(request.getExpectedVersion())) {
            throw new StaleVersionException("The record changed. Reload before editing.", brand.getVersion());
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            String trimmedName = request.getName().trim();
            if (brandRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
                throw new DuplicateResourceException("Brand with name '" + trimmedName + "' already exists.");
            }
            brand.setName(trimmedName);
        }

        if (request.getIsActive() != null) {
            brand.setActive(request.getIsActive());
        }

        brand.setUpdatedAt(businessClock.now());
        brand.setUpdatedBy(currentActorId);

        Brand saved = brandRepository.save(brand);
        log.info("Updated brand ID: {} version: {}", saved.getId(), saved.getVersion());
        return toDto(saved);
    }

    public BrandDto toDto(Brand brand) {
        return BrandDto.builder()
                .id(brand.getId())
                .name(brand.getName())
                .isActive(brand.isActive())
                .version(brand.getVersion())
                .createdAt(brand.getCreatedAt())
                .updatedAt(brand.getUpdatedAt())
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
