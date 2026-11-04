/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A passenger-facing notice posted by the admin (route diversion, bus
 * breakdown, strike, etc.). Shown as a banner at the top of the index page
 * while it is active and not expired.
 */
@Entity
@Table(name = "notice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notice {

    public static final String INFO = "INFO";
    public static final String WARNING = "WARNING";
    public static final String CRITICAL = "CRITICAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    /** INFO, WARNING or CRITICAL - controls banner colour. */
    @Column(nullable = false, length = 10)
    private String severity = INFO;

    /** Optional: the notice concerns this route only (purely informational for now). */
    @Column(length = 40)
    private String routeCode;

    /** Optional: the notice concerns this bus only. */
    @Column(length = 40)
    private String busNumber;

    /** Admin can switch a notice off without deleting it. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** null = stays up until the admin removes it. */
    private LocalDateTime expiresAt;

    public boolean isLive(LocalDateTime now) {
        return active && (expiresAt == null || expiresAt.isAfter(now));
    }
}
