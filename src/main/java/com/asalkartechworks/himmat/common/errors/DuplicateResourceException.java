package com.asalkartechworks.himmat.common.errors;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends AppException {
    public DuplicateResourceException(String message) {
        super("DUPLICATE_RESOURCE", message, HttpStatus.CONFLICT);
    }
}
