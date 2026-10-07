/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

/**
 * Stops the app from waiting on a Redis that is down.
 *
 * Every call to an unreachable Redis blocks until its timeout (2 seconds here). A passenger search
 * reads the last location of EVERY active ride, so with Redis down that was one 2-second wait per
 * bus: the request dragged on for many seconds and the page showed nothing at all.
 *
 * After a failure the breaker "opens" for a short cool-down (default 30 s). While it is open, callers
 * skip Redis immediately and use their fallback (in-memory last location, then the database), so
 * answers stay fast. When the cool-down ends the next call tries Redis again; if that works the breaker
 * closes, if not it re-opens.
 */
@Component
@Slf4j
public class RedisCircuitBreaker {

    @Value("${wimb.redis.circuit-cooldown-seconds:30}")
    private long cooldownSeconds;

    // replaceable in tests
    private LongSupplier clock = System::currentTimeMillis;

    private volatile long openUntil = 0;

    /** True when it is fine to call Redis now (closed, or the cool-down has ended and we may try again). */
    public boolean allowCall() {
        return clock.getAsLong() >= openUntil;
    }

    public boolean isOpen() {
        return !allowCall();
    }

    public void recordSuccess() {
        if (openUntil != 0) {
            openUntil = 0;
            log.info("Redis is reachable again");
        }
    }

    public void recordFailure(Throwable cause) {
        boolean wasClosed = allowCall();
        openUntil = clock.getAsLong() + Math.max(1, cooldownSeconds) * 1000;
        if (wasClosed) {
            log.warn("Redis unavailable ({}): skipping Redis for {}s, using in-memory and database fallbacks",
                    cause == null ? "unknown" : cause.getMessage(), Math.max(1, cooldownSeconds));
        }
    }
}
