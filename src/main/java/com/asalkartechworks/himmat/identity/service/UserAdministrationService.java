package com.asalkartechworks.himmat.identity.service;

import com.asalkartechworks.himmat.common.errors.DuplicateResourceException;
import com.asalkartechworks.himmat.common.errors.InvalidOperationException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.StaleVersionException;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.identity.dto.CreateUserRequest;
import com.asalkartechworks.himmat.identity.dto.UpdateUserRequest;
import com.asalkartechworks.himmat.identity.dto.UserSummaryDto;
import com.asalkartechworks.himmat.identity.model.AppUser;
import com.asalkartechworks.himmat.identity.model.Permission;
import com.asalkartechworks.himmat.identity.model.UserRole;
import com.asalkartechworks.himmat.identity.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdministrationService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "username", "displayName", "role", "isActive", "createdAt", "updatedAt"
    );

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BusinessClock businessClock;

    @Transactional(readOnly = true)
    public Page<UserSummaryDto> getUsers(UserRole role, Boolean isActive, Pageable pageable) {
        Pageable sanitizedPageable = sanitizePageable(pageable);
        return userRepository.searchUsers(role, isActive, sanitizedPageable)
                .map(this::toSummaryDto);
    }

    @Transactional
    public UserSummaryDto createUser(CreateUserRequest request, Long currentActorId) {
        String normalizedUsername = request.getUsername().trim().toLowerCase();

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new DuplicateResourceException("Username '" + normalizedUsername + "' is already registered.");
        }

        Instant now = businessClock.now();
        AppUser newUser = AppUser.builder()
                .username(normalizedUsername)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName().trim())
                .role(request.getRole())
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(currentActorId)
                .updatedBy(currentActorId)
                .build();

        AppUser saved = userRepository.save(newUser);
        log.info("Created new user ID: {} with role: {}", saved.getId(), saved.getRole());
        return toSummaryDto(saved);
    }

    @Transactional
    public UserSummaryDto updateUser(Long id, UpdateUserRequest request, Long currentActorId) {
        AppUser user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", String.valueOf(id)));

        // Optimistic concurrency check
        if (!user.getVersion().equals(request.getExpectedVersion())) {
            throw new StaleVersionException(
                    "The record changed. Reload before editing.",
                    user.getVersion()
            );
        }

        boolean isCurrentlyActiveOwner = (user.getRole() == UserRole.OWNER && user.isActive());
        UserRole targetRole = request.getRole() != null ? request.getRole() : user.getRole();
        boolean targetActive = request.getIsActive() != null ? request.getIsActive() : user.isActive();
        boolean willRemainActiveOwner = (targetRole == UserRole.OWNER && targetActive);

        // Transactional locking check: Protect against demoting or disabling the last active Owner
        if (isCurrentlyActiveOwner && !willRemainActiveOwner) {
            List<AppUser> activeOwners = userRepository.findActiveUsersForUpdate(UserRole.OWNER);
            if (activeOwners.size() <= 1) {
                throw new InvalidOperationException("LAST_OWNER_PROTECTION", "Cannot demote or disable the last active Owner.");
            }
        }

        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName().trim());
        }

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        if (request.getIsActive() != null) {
            user.setActive(request.getIsActive());
        }

        user.setUpdatedAt(businessClock.now());
        user.setUpdatedBy(currentActorId);

        AppUser saved = userRepository.save(user);
        log.info("Updated user ID: {} with version: {}", saved.getId(), saved.getVersion());
        return toSummaryDto(saved);
    }

    public UserSummaryDto toSummaryDto(AppUser user) {
        return UserSummaryDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .isActive(user.isActive())
                .version(user.getVersion())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .permissions(Permission.getPermissionsForRole(user.getRole()))
                .build();
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);

        Sort safeSort = Sort.by(Sort.Direction.ASC, "id");
        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort()) {
                if (ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    safeSort = Sort.by(order.getDirection(), order.getProperty())
                            .and(Sort.by(Sort.Direction.ASC, "id"));
                    break;
                }
            }
        }
        return PageRequest.of(page, size, safeSort);
    }
}
