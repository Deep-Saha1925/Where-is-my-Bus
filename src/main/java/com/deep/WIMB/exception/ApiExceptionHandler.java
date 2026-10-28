/*
 * Where Is My Bus (WIMB)
 * Copyright (c) 2025-2026 Deep Saha. All rights reserved.
 * Proprietary and confidential. See the LICENSE file in the project root.
 * Unauthorized copying, modification, distribution, hosting or use is prohibited.
 */
package com.deep.WIMB.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.deep.WIMB.controller")
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(DriverNotVerifiedException.class)
    public ResponseEntity<?> handleDriverNotVerified(DriverNotVerifiedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage()));
    }

    // Keep intended status codes (e.g. 404 from RouteController) instead of
    // letting the catch-all below turn them into a 500.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason() != null ? ex.getReason() : "Request failed"));
    }

    // Deliberate, short business errors thrown by the services (e.g. "Ride already ended")
    // are shown to the caller. Anything that looks technical -- SQL, Hibernate, Redis,
    // class names, stack fragments -- is logged server-side and replaced by a generic
    // message, so internals (table names, hosts, queries) never reach the browser.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex) {
        log.error("Unhandled exception in controller layer", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", publicMessage(ex)));
    }

    private static String publicMessage(Exception ex) {
        Class<?> type = ex.getClass();
        boolean deliberate = type == RuntimeException.class
                || type == IllegalStateException.class
                || type == IllegalArgumentException.class;
        String msg = ex.getMessage();
        if (deliberate && msg != null && msg.length() <= 120 && msg.indexOf('\n') < 0) {
            String lower = msg.toLowerCase();
            boolean technical = lower.contains("sql") || lower.contains("jdbc") || lower.contains("hibernate")
                    || lower.contains("redis") || lower.contains("exception") || lower.contains("org.")
                    || lower.contains("java.") || lower.contains("com.deep");
            if (!technical) return msg;
        }
        return "Unexpected server error";
    }
}
