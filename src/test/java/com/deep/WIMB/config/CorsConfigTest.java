/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsConfigTest {

    @Test
    void emptyMeansSameOriginOnly() {
        assertDoesNotThrow(() -> new CorsConfig(""));
        assertDoesNotThrow(() -> new CorsConfig("   ,  ,"));
    }

    @Test
    void acceptsExactOrigins() {
        assertDoesNotThrow(() -> new CorsConfig("http://localhost:5500, http://127.0.0.1:5500"));
    }

    @Test
    void wildcardsAreRejectedBecauseCredentialsAreAllowed() {
        assertThrows(IllegalStateException.class, () -> new CorsConfig("*"));
        assertThrows(IllegalStateException.class, () -> new CorsConfig("https://*.example.com"));
        assertThrows(IllegalStateException.class, () -> new CorsConfig("http://localhost:5500,*"));
    }
}
