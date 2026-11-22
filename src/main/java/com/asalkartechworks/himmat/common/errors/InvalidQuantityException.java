package com.asalkartechworks.himmat.common.errors;

import org.springframework.http.HttpStatus;

public class InvalidQuantityException extends AppException {
    public InvalidQuantityException(String message) {
        super("INVALID_QUANTITY", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
