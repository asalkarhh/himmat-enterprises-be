package com.asalkartechworks.himmat.common.errors;

import org.springframework.http.HttpStatus;

public class StaleVersionException extends AppException {
    public StaleVersionException(String message, Long currentVersion) {
        super("STALE_VERSION", message, HttpStatus.CONFLICT, currentVersion);
    }
}
