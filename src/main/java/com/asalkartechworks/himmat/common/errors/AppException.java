package com.asalkartechworks.himmat.common.errors;

import com.asalkartechworks.himmat.common.dto.FieldErrorDto;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

@Getter
public class AppException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Long currentVersion;
    private final List<FieldErrorDto> fields;

    public AppException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
        this.currentVersion = null;
        this.fields = new ArrayList<>();
    }

    public AppException(String code, String message, HttpStatus status, Long currentVersion) {
        super(message);
        this.code = code;
        this.status = status;
        this.currentVersion = currentVersion;
        this.fields = new ArrayList<>();
    }

    public AppException(String code, String message, HttpStatus status, List<FieldErrorDto> fields) {
        super(message);
        this.code = code;
        this.status = status;
        this.currentVersion = null;
        this.fields = fields != null ? fields : new ArrayList<>();
    }
}
