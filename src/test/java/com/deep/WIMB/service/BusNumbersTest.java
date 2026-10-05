/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusNumbersTest {

    @Test
    void ignoresCaseSpacesAndPunctuation() {
        assertEquals("wb23a1245", BusNumbers.normalize("WB-23A-1245"));
        assertEquals("wb23a1245", BusNumbers.normalize(" wb 23a  1245 "));
        assertEquals("wb23a1245", BusNumbers.normalize("WB_23A/1245."));
    }

    @Test
    void differentSpellingsOfOneBusGiveTheSameQuery() {
        assertEquals(BusNumbers.normalize("test_123"), BusNumbers.normalize("TEST 123"));
    }

    @Test
    void nullAndPunctuationOnlyGiveEmpty() {
        assertEquals("", BusNumbers.normalize(null));
        assertEquals("", BusNumbers.normalize("---"));
        assertEquals("", BusNumbers.normalize("   "));
    }

    @Test
    void sqlWildcardsAreStrippedSoTheyCannotWidenTheSearch() {
        // % and _ are LIKE wildcards; the query must never contain them
        assertEquals("ab", BusNumbers.normalize("a%b"));
        assertEquals("ab", BusNumbers.normalize("a_b"));
        assertEquals("abc", BusNumbers.normalize("'; a%_b c"));
    }

    @Test
    void overlongInputIsCut() {
        String longInput = "a".repeat(200);
        assertEquals(BusNumbers.MAX_QUERY_LENGTH, BusNumbers.normalize(longInput).length());
    }
}
