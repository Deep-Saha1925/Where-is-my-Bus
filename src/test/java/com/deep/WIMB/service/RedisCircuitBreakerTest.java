/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.TestFields;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisCircuitBreakerTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private RedisCircuitBreaker breaker(long cooldownSeconds) {
        RedisCircuitBreaker b = new RedisCircuitBreaker();
        TestFields.set(b, "cooldownSeconds", cooldownSeconds);
        LongSupplier fakeClock = now::get;
        TestFields.set(b, "clock", fakeClock);
        return b;
    }

    @Test
    void startsClosedSoRedisIsUsed() {
        RedisCircuitBreaker b = breaker(30);
        assertTrue(b.allowCall());
        assertFalse(b.isOpen());
    }

    @Test
    void afterAFailureRedisIsSkippedUntilTheCooldownEnds() {
        RedisCircuitBreaker b = breaker(30);
        b.recordFailure(new RuntimeException("down"));

        assertFalse(b.allowCall(), "skipped right after the failure");
        now.addAndGet(29_000);
        assertFalse(b.allowCall(), "still skipped at 29 s");
        now.addAndGet(1_000);
        assertTrue(b.allowCall(), "one trial call is allowed once 30 s have passed");
    }

    @Test
    void aSuccessfulTrialClosesItAgain() {
        RedisCircuitBreaker b = breaker(30);
        b.recordFailure(new RuntimeException("down"));
        now.addAndGet(31_000);
        b.recordSuccess();

        assertTrue(b.allowCall());
        // and a failure later starts a fresh cool-down
        b.recordFailure(new RuntimeException("down again"));
        assertFalse(b.allowCall());
    }

    @Test
    void aFailedTrialOpensItForAnotherFullCooldown() {
        RedisCircuitBreaker b = breaker(30);
        b.recordFailure(new RuntimeException("down"));
        now.addAndGet(31_000);
        assertTrue(b.allowCall());

        b.recordFailure(new RuntimeException("still down"));
        assertFalse(b.allowCall());
        now.addAndGet(29_000);
        assertFalse(b.allowCall());
        now.addAndGet(1_000);
        assertTrue(b.allowCall());
    }

    @Test
    void aCooldownOfZeroStillWaitsAtLeastOneSecond() {
        RedisCircuitBreaker b = breaker(0);
        b.recordFailure(null);
        assertFalse(b.allowCall());
        now.addAndGet(1_000);
        assertTrue(b.allowCall());
    }

    @Test
    void successWhileClosedChangesNothing() {
        RedisCircuitBreaker b = breaker(30);
        b.recordSuccess();
        assertTrue(b.allowCall());
    }
}
