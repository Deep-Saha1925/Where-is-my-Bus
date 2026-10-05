/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.config;

import com.deep.WIMB.TestFields;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StartupConfigValidatorTest {

    private static StartupConfigValidator validator(String user, String password, boolean enforce) {
        StartupConfigValidator v = new StartupConfigValidator();
        TestFields.set(v, "adminUsername", user);
        TestFields.set(v, "adminPassword", password);
        TestFields.set(v, "enforce", enforce);
        return v;
    }

    @Test
    void strongPasswordPassesEvenWhenEnforced() {
        assertDoesNotThrow(() -> validator("deep-admin", "Tr4ck-Buses!Safely-2026", true).validate());
    }

    @Test
    void weakPasswordOnlyWarnsByDefault() {
        assertDoesNotThrow(() -> validator("admin", "short", false).validate());
    }

    @Test
    void weakPasswordIsRefusedWhenEnforced() {
        assertThrows(IllegalStateException.class, () -> validator("admin", "short", true).validate());
    }

    @Test
    void rejectsBlankCommonAndSameAsUsername() {
        assertThrows(IllegalStateException.class, () -> validator("admin", "", true).validate());
        assertThrows(IllegalStateException.class, () -> validator("", "Tr4ck-Buses!Safely-2026", true).validate());
        assertThrows(IllegalStateException.class, () -> validator("admin", "password123", true).validate());
        assertThrows(IllegalStateException.class,
                () -> validator("administrator1", "administrator1", true).validate());
    }

    @Test
    void rejectsPasswordsMadeOfAFewRepeatedCharacters() {
        assertThrows(IllegalStateException.class, () -> validator("admin", "aaaaaaaaaaaaaaaa", true).validate());
    }

    @Test
    void exactlyTwelveCharactersIsLongEnough() {
        assertDoesNotThrow(() -> validator("admin", "Abcdef-12345", true).validate());
        assertThrows(IllegalStateException.class, () -> validator("admin", "Abcde-12345", true).validate());
    }
}
