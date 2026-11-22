package com.asalkartechworks.himmat.identity.service;

import com.asalkartechworks.himmat.common.errors.AuthenticationException;
import com.asalkartechworks.himmat.common.errors.RateLimitException;
import com.asalkartechworks.himmat.common.errors.ResourceNotFoundException;
import com.asalkartechworks.himmat.common.errors.ValidationException;
import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.identity.dto.ChangePasswordRequest;
import com.asalkartechworks.himmat.identity.dto.LoginRequest;
import com.asalkartechworks.himmat.identity.dto.LoginResponse;
import com.asalkartechworks.himmat.identity.dto.UserSummaryDto;
import com.asalkartechworks.himmat.identity.model.AppUser;
import com.asalkartechworks.himmat.identity.model.Permission;
import com.asalkartechworks.himmat.identity.repository.AppUserRepository;
import com.asalkartechworks.himmat.security.JwtService;
import com.asalkartechworks.himmat.security.LoginThrottleService;
import com.asalkartechworks.himmat.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginThrottleService throttleService;
    private final BusinessClock businessClock;

    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        String normalizedUsername = request.getUsername() != null ? request.getUsername().trim().toLowerCase() : "";

        // Check rate limiting / login throttling
        if (throttleService.isThrottled(normalizedUsername, clientIp)) {
            long retryAfter = throttleService.getRetryAfterSeconds(normalizedUsername, clientIp);
            throw new RateLimitException("Too many failed login attempts. Please try again later.", retryAfter);
        }

        Optional<AppUser> userOpt = userRepository.findByUsername(normalizedUsername);

        // Disclose nothing about username existence on bad credentials
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPasswordHash())) {
            throttleService.recordFailure(normalizedUsername, clientIp);
            throw new AuthenticationException("Invalid username or password.");
        }

        AppUser user = userOpt.get();

        if (!user.isActive()) {
            throttleService.recordFailure(normalizedUsername, clientIp);
            throw new AuthenticationException("User account is inactive. Please contact administrator.");
        }

        // Reset failed login attempts on success
        throttleService.reset(normalizedUsername, clientIp);

        UserPrincipal principal = UserPrincipal.create(user);
        String token = jwtService.generateToken(principal);

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationSeconds())
                .user(toSummaryDto(user))
                .build();
    }

    @Transactional(readOnly = true)
    public UserSummaryDto getCurrentUser(UserPrincipal principal) {
        AppUser user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", String.valueOf(principal.getId())));
        return toSummaryDto(user);
    }

    @Transactional
    public UserSummaryDto changePassword(Long userId, ChangePasswordRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", String.valueOf(userId)));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AuthenticationException("Current password is incorrect.");
        }

        if (request.getNewPassword() == null || request.getNewPassword().length() < 12) {
            throw new ValidationException("New password must be at least 12 characters long.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(businessClock.now());
        user.setUpdatedBy(userId);

        // Saving triggers @Version increment, which automatically invalidates prior active JWT tokens
        AppUser saved = userRepository.save(user);
        log.info("Password successfully changed for user ID: {}", saved.getId());

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
}
