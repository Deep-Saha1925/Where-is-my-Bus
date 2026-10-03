/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks the admin credentials once at startup so a weak or placeholder password is noticed
 * immediately instead of sitting in production unnoticed.
 *
 * By default it only logs a loud warning (so local development is never blocked). Set
 * ENFORCE_STRONG_ADMIN_PASSWORD=true (wimb.security.enforce-strong-admin-password) in
 * production and the application refuses to start until the password is fixed.
 *
 * Secret values are never logged -- only what is wrong with them.
 */
@Component
@Slf4j
public class StartupConfigValidator {

    private static final int MIN_PASSWORD_LENGTH = 12;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "admin", "admin123", "administrator", "password", "password1", "password123",
            "12345678", "123456789", "1234567890", "qwerty123", "letmein", "change-me",
            "change-me-to-a-long-random-password", "wimb", "wimb123", "bus12345"
    );

    @Value("${wimb.admin.username:}")
    private String adminUsername;

    @Value("${wimb.admin.password:}")
    private String adminPassword;

    @Value("${wimb.security.enforce-strong-admin-password:false}")
    private boolean enforce;

    @PostConstruct
    void validate() {
        List<String> problems = new ArrayList<>();

        if (adminUsername == null || adminUsername.isBlank()) {
            problems.add("ADMIN_USERNAME is empty");
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            problems.add("ADMIN_PASSWORD is empty");
        } else {
            if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
                problems.add("ADMIN_PASSWORD is shorter than " + MIN_PASSWORD_LENGTH + " characters");
            }
            if (COMMON_PASSWORDS.contains(adminPassword.toLowerCase())) {
                problems.add("ADMIN_PASSWORD is a common/placeholder password");
            }
            if (adminPassword.equalsIgnoreCase(adminUsername)) {
                problems.add("ADMIN_PASSWORD is the same as ADMIN_USERNAME");
            }
            if (adminPassword.chars().distinct().count() < 5) {
                problems.add("ADMIN_PASSWORD uses too few different characters");
            }
        }

        if (problems.isEmpty()) return;

        String message = "Weak admin configuration: " + String.join("; ", problems)
                + ". Set a long random ADMIN_PASSWORD (see ..env.example).";

        if (enforce) {
            throw new IllegalStateException(message + " Startup refused because "
                    + "ENFORCE_STRONG_ADMIN_PASSWORD=true.");
        }
        log.warn("⚠️  {}", message);
    }
}