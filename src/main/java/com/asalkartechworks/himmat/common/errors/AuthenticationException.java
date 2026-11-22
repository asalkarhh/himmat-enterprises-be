package com.asalkartechworks.himmat.common.errors;

import org.springframework.http.HttpStatus;

public class AuthenticationException extends AppException {
    public AuthenticationException(String message) {
        super("UNAUTHENTICATED", message, HttpStatus.UNAUTHORIZED);
    }
}
