package com.asalkartechworks.himmat.common.errors;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends AppException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}
