package com.asalkartechworks.himmat.common.errors;

import com.asalkartechworks.himmat.common.dto.FieldErrorDto;
import org.springframework.http.HttpStatus;

import java.util.List;

public class ValidationException extends AppException {
    public ValidationException(String message, List<FieldErrorDto> fields) {
        super("VALIDATION_FAILED", message, HttpStatus.UNPROCESSABLE_ENTITY, fields);
    }

    public ValidationException(String message) {
        super("VALIDATION_FAILED", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
