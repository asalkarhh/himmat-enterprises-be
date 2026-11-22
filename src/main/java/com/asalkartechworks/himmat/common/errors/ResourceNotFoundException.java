package com.asalkartechworks.himmat.common.errors;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String resource, String identifier) {
        super("NOT_FOUND", resource + " not found with identifier: " + identifier, HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
