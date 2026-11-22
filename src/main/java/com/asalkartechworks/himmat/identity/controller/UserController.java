package com.asalkartechworks.himmat.identity.controller;

import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.common.dto.PageResponse;
import com.asalkartechworks.himmat.identity.dto.CreateUserRequest;
import com.asalkartechworks.himmat.identity.dto.UpdateUserRequest;
import com.asalkartechworks.himmat.identity.dto.UserSummaryDto;
import com.asalkartechworks.himmat.identity.model.UserRole;
import com.asalkartechworks.himmat.identity.service.UserAdministrationService;
import com.asalkartechworks.himmat.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
@Tag(name = "User Administration", description = "User creation, listing, and updates (Owner-only)")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserAdministrationService userAdministrationService;

    @GetMapping
    @Operation(summary = "List users", description = "Paginated list of system users with optional role and active status filtering")
    public ResponseEntity<PageResponse<UserSummaryDto>> getUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<UserSummaryDto> page = userAdministrationService.getUsers(role, isActive, pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @PostMapping
    @Operation(summary = "Create user", description = "Create a new OWNER or MANAGER user account")
    public ResponseEntity<ApiResponse<UserSummaryDto>> createUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateUserRequest request) {
        UserSummaryDto user = userAdministrationService.createUser(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(user));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update user", description = "Update display name, role, or active status with expectedVersion checking")
    public ResponseEntity<ApiResponse<UserSummaryDto>> updateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateUserRequest request) {
        UserSummaryDto user = userAdministrationService.updateUser(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.of(user));
    }
}
