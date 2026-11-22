package com.asalkartechworks.himmat.identity.service;

import com.asalkartechworks.himmat.common.time.BusinessClock;
import com.asalkartechworks.himmat.identity.model.AppUser;
import com.asalkartechworks.himmat.identity.model.UserRole;
import com.asalkartechworks.himmat.identity.repository.AppUserRepository;
import com.asalkartechworks.himmat.settings.model.ShopSettings;
import com.asalkartechworks.himmat.settings.repository.ShopSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Controlled initial bootstrap runner for creating the primary OWNER account and shop settings.
 * Enabled ONLY when 'app.bootstrap.enabled=true' is explicitly passed via configuration.
 * Disabled by default during ordinary application startup.
 * Safe against re-runs: will never overwrite an existing account or shop configuration.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OwnerBootstrapService implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final ShopSettingsRepository shopSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final BusinessClock businessClock;

    @Value("${app.bootstrap.username:}")
    private String bootstrapUsername;

    @Value("${app.bootstrap.display-name:}")
    private String bootstrapDisplayName;

    @Value("${app.bootstrap.password:}")
    private String bootstrapPassword;

    @Value("${app.bootstrap.shop-name:Himmat Enterprises}")
    private String bootstrapShopName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("OwnerBootstrapService triggered: verifying bootstrap requirements...");

        if (!StringUtils.hasText(bootstrapUsername) || !StringUtils.hasText(bootstrapDisplayName) || !StringUtils.hasText(bootstrapPassword)) {
            log.warn("Bootstrap skipped: required environment variables (APP_BOOTSTRAP_USERNAME, APP_BOOTSTRAP_DISPLAY_NAME, APP_BOOTSTRAP_PASSWORD) are not fully set.");
            return;
        }

        if (bootstrapPassword.length() < 12) {
            log.error("Bootstrap aborted: bootstrap password must be at least 12 characters long.");
            return;
        }

        String normalizedUsername = bootstrapUsername.trim().toLowerCase();

        // Safe re-run check: verify if an OWNER already exists
        if (userRepository.existsByRole(UserRole.OWNER)) {
            log.info("Bootstrap skipped: An OWNER account already exists in the system. Existing accounts and settings were preserved.");
            return;
        }

        if (userRepository.existsByUsername(normalizedUsername)) {
            log.warn("Bootstrap skipped: Username '{}' already exists with a different role. Preserving existing record.", normalizedUsername);
            return;
        }

        Instant now = businessClock.now();

        // 1. Create Initial Owner
        AppUser owner = AppUser.builder()
                .username(normalizedUsername)
                .displayName(bootstrapDisplayName.trim())
                .passwordHash(passwordEncoder.encode(bootstrapPassword))
                .role(UserRole.OWNER)
                .isActive(true)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        AppUser savedOwner = userRepository.save(owner);
        savedOwner.setCreatedBy(savedOwner.getId());
        savedOwner.setUpdatedBy(savedOwner.getId());
        userRepository.save(savedOwner);

        // 2. Initialize Shop Settings singleton if missing
        if (shopSettingsRepository.findSingleton().isEmpty()) {
            ShopSettings settings = ShopSettings.builder()
                    .id(ShopSettings.SINGLETON_ID)
                    .name(StringUtils.hasText(bootstrapShopName) ? bootstrapShopName.trim() : "Himmat Enterprises")
                    .currency("INR")
                    .timezone("Asia/Kolkata")
                    .creditDays(30)
                    .invoicePrefix("HE")
                    .version(0L)
                    .createdAt(now)
                    .updatedAt(now)
                    .createdBy(savedOwner.getId())
                    .updatedBy(savedOwner.getId())
                    .build();
            shopSettingsRepository.save(settings);
            log.info("Initialized default shop settings singleton (id=1) for '{}'.", settings.getName());
        }

        log.info("Initial OWNER account successfully bootstrapped for username '{}'.", normalizedUsername);
        log.info("SECURITY NOTICE: Disable bootstrap by setting APP_BOOTSTRAP_ENABLED=false (or app.bootstrap.enabled=false).");
    }
}
