/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A depot account: owns a set of buses and logs in (ROLE_DEPOT) to see only those.
 * Created by the admin. Separate from the drivers' depot-code list (Excel), which
 * keeps working unchanged.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Depot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Upper-case display name, e.g. ALIPURDUAR. */
    @Column(unique = true, nullable = false)
    private String name;

    /** Lower-case login name. */
    @Column(unique = true, nullable = false)
    private String username;

    /** BCrypt hash, never the plain password. */
    @Column(nullable = false)
    private String passwordHash;

    private boolean enabled = true;
}
