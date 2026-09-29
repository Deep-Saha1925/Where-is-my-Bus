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

    // Previously this had no logging at all -- every 500 was silently
    // swallowed into a JSON response with no trace printed anywhere,
    // which is exactly what made this bug invisible in the console.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex) {
        log.error("Unhandled exception in controller layer", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Unexpected server error"));
    }
}