package com.asalkartechworks.himmat.identity.controller;

import com.asalkartechworks.himmat.common.dto.ApiResponse;
import com.asalkartechworks.himmat.identity.dto.ChangePasswordRequest;
import com.asalkartechworks.himmat.identity.dto.LoginRequest;
import com.asalkartechworks.himmat.identity.dto.LoginResponse;
import com.asalkartechworks.himmat.identity.dto.UserSummaryDto;
import com.asalkartechworks.himmat.identity.service.AuthService;
import com.asalkartechworks.himmat.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for login, identity profile, and password management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate using username and password to obtain a signed JWT access token")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        LoginResponse response = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get current user profile",
            description = "Retrieve current authenticated user details and resolved permissions",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<ApiResponse<UserSummaryDto>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserSummaryDto user = authService.getCurrentUser(principal);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    @PostMapping("/change-password")
    @Operation(
            summary = "Change password",
            description = "Change the authenticated user's password; invalidates previous active JWT tokens",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    public ResponseEntity<ApiResponse<UserSummaryDto>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        UserSummaryDto user = authService.changePassword(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.of(user));
    }
}
