/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import com.deep.WIMB.TestFields;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimiterTest {

    private static LoginRateLimiter limiter(long windowSeconds, long lockoutSeconds,
                                            int perIp, int perDepot, int perBus) {
        LoginRateLimiter l = new LoginRateLimiter();
        TestFields.set(l, "windowSeconds", windowSeconds);
        TestFields.set(l, "lockoutSeconds", lockoutSeconds);
        TestFields.set(l, "maxPerIp", perIp);
        TestFields.set(l, "maxPerDepot", perDepot);
        TestFields.set(l, "maxPerBus", perBus);
        return l;
    }

    private static LoginRateLimiter defaults() {
        return limiter(600, 900, 10, 40, 8);
    }

    @Test
    void freshClientIsNotBlocked() {
        assertFalse(defaults().check("1.1.1.1", "ALIPURDUAR", "WB1").blocked());
    }

    @Test
    void blocksAfterTheBusLimitIsReached() {
        LoginRateLimiter l = defaults();
        for (int i = 0; i < 7; i++) l.recordFailure("1.1.1.1", "ALIPURDUAR", "WB1");
        assertFalse(l.check("1.1.1.1", "ALIPURDUAR", "WB1").blocked(), "7 failures are still allowed");

        l.recordFailure("1.1.1.1", "ALIPURDUAR", "WB1");
        LoginRateLimiter.Verdict verdict = l.check("1.1.1.1", "ALIPURDUAR", "WB1");
        assertTrue(verdict.blocked(), "the 8th failure locks the bus");
        assertTrue(verdict.retryAfterSeconds() > 0 && verdict.retryAfterSeconds() <= 900);
    }

    @Test
    void busLockAppliesFromAnyIpAndIgnoresCaseAndSpaces() {
        LoginRateLimiter l = defaults();
        for (int i = 0; i < 8; i++) l.recordFailure("1.1.1.1", "ALIPURDUAR", "WB1");
        assertTrue(l.check("9.9.9.9", "alipurduar ", " wb1").blocked());
        assertFalse(l.check("9.9.9.9", "ALIPURDUAR", "WB2").blocked(), "another bus is unaffected");
    }

    @Test
    void oneIpFailingAcrossManyBusesGetsLockedOut() {
        LoginRateLimiter l = defaults();
        for (int i = 0; i < 10; i++) l.recordFailure("2.2.2.2", "D" + i, "B" + i);
        assertTrue(l.check("2.2.2.2", "OTHER", "BX").blocked());
        assertFalse(l.check("3.3.3.3", "OTHER", "BX").blocked(), "other IPs are unaffected");
    }

    @Test
    void manyIpsAgainstOneDepotLockTheDepot() {
        LoginRateLimiter l = defaults();
        for (int i = 0; i < 40; i++) l.recordFailure("10.0.0." + i, "DEPOT", "BUS" + i);
        assertTrue(l.check("77.7.7.7", "DEPOT", "NEWBUS").blocked());
        assertFalse(l.check("77.7.7.7", "OTHERDEPOT", "NEWBUS").blocked());
    }

    @Test
    void lockExpiresAndCountingStartsFresh() throws InterruptedException {
        LoginRateLimiter l = limiter(1, 1, 2, 40, 8);
        l.recordFailure("4.4.4.4", "D", "B");
        l.recordFailure("4.4.4.4", "D", "B");
        assertTrue(l.check("4.4.4.4", "D", "B").blocked());

        Thread.sleep(1200);
        assertFalse(l.check("4.4.4.4", "D", "B").blocked(), "lock is over");

        l.recordFailure("4.4.4.4", "D", "B");
        assertFalse(l.check("4.4.4.4", "D", "B").blocked(), "one new failure is not enough to lock again");
    }

    @Test
    void checkingNeverCountsAnything() {
        LoginRateLimiter l = defaults();
        for (int i = 0; i < 100; i++) l.check("5.5.5.5", "D", "B");
        assertFalse(l.check("5.5.5.5", "D", "B").blocked());
    }
}
