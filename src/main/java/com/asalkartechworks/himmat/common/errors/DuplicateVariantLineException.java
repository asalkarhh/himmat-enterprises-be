package com.asalkartechworks.himmat.common.errors;

import org.springframework.http.HttpStatus;

public class DuplicateVariantLineException extends AppException {
    public DuplicateVariantLineException(String message) {
        super("DUPLICATE_VARIANT_LINE", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
