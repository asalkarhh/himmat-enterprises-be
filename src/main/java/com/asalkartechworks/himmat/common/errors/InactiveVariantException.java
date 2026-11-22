package com.asalkartechworks.himmat.common.errors;

import org.springframework.http.HttpStatus;

public class InactiveVariantException extends AppException {
    public InactiveVariantException(String message) {
        super("INACTIVE_VARIANT", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
