package com.deep.WIMB.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class DriverNotVerifiedException extends RuntimeException {
    public DriverNotVerifiedException(String message) {
        super(message);
    }
}