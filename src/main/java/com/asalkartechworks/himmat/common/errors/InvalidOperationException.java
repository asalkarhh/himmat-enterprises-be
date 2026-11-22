package com.asalkartechworks.himmat.common.errors;

import org.springframework.http.HttpStatus;

public class InvalidOperationException extends AppException {
    public InvalidOperationException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }

    public InvalidOperationException(String message) {
        super("INVALID_OPERATION", message, HttpStatus.CONFLICT);
    }
}
