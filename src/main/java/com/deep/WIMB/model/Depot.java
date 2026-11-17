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
 * Owner of a set of buses. Just the depot's name, matching the name in the admin's
 * "Depot Codes" list. The login password is that list's code, so nothing secret is stored here.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Depot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Upper-case depot name, same as in the depot codes list. */
    @Column(unique = true, nullable = false)
    private String name;
}
