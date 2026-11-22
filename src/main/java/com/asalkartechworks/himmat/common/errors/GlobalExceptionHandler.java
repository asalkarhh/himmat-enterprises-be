package com.asalkartechworks.himmat.common.errors;

import com.asalkartechworks.himmat.common.dto.ErrorDetails;
import com.asalkartechworks.himmat.common.dto.ErrorResponse;
import com.asalkartechworks.himmat.common.dto.FieldErrorDto;
import com.asalkartechworks.himmat.common.filter.CorrelationIdHolder;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        log.warn("Application exception [{}]: {} (status: {})", ex.getCode(), ex.getMessage(), ex.getStatus());
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RateLimitException rle) {
            headers.add("Retry-After", String.valueOf(rle.getRetryAfterSeconds()));
        }
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorDetails.builder()
                        .code(ex.getCode())
                        .message(ex.getMessage())
                        .fields(ex.getFields())
                        .currentVersion(ex.getCurrentVersion())
                        .build())
                .requestId(CorrelationIdHolder.get())
                .build();
        return new ResponseEntity<>(body, headers, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        List<FieldErrorDto> fields = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.add(new FieldErrorDto(fe.getField(), fe.getDefaultMessage()));
        }
        log.warn("Validation failed for request: {}", fields);
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorDetails.builder()
                        .code("VALIDATION_FAILED")
                        .message("Validation failed for one or more fields.")
                        .fields(fields)
                        .build())
                .requestId(CorrelationIdHolder.get())
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDto> fields = new ArrayList<>();
        ex.getConstraintViolations().forEach(cv ->
                fields.add(new FieldErrorDto(cv.getPropertyPath().toString(), cv.getMessage()))
        );
        log.warn("Constraint violation: {}", fields);
        ErrorResponse body = ErrorResponse.builder()
                .error(ErrorDetails.builder()
                        .code("VALIDATION_FAILED")
                        .message("Validation constraints violated.")
                        .fields(fields)
                        .build())
                .requestId(CorrelationIdHolder.get())
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed HTTP message: {}", ex.getMessage());
        String message = "Malformed JSON request or invalid input value.";
        if (ex.getCause() instanceof UnrecognizedPropertyException upe) {
            message = "Unrecognized field: '" + upe.getPropertyName() + "'";
        }
        ErrorResponse body = ErrorResponse.of("MALFORMED_JSON", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Method argument type mismatch: parameter '{}' with value '{}'", ex.getName(), ex.getValue());
        ErrorResponse body = ErrorResponse.of("MALFORMED_INPUT", "Invalid value for parameter: " + ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleBadCredentials(Exception ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of("UNAUTHENTICATED", "Invalid username or password.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex) {
        log.warn("Disabled user attempted login: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of("UNAUTHENTICATED", "User account is inactive.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of("FORBIDDEN", "Insufficient permissions to perform this operation.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Static or endpoint resource not found: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of("NOT_FOUND", "Requested resource was not found.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(OptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of("STALE_VERSION", "The record changed. Reload before editing.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity conflict: {}", ex.getMessage());
        String message = "Database constraint violation occurred.";
        String mostSpecific = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
        if (mostSpecific.contains("uq_app_user_username")) {
            message = "Username is already registered.";
        }
        if (mostSpecific.contains("chk_inventory_balance_non_negative")) {
            ErrorResponse body = ErrorResponse.of("INSUFFICIENT_STOCK", "Operation failed because stock quantity cannot become negative.");
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
        }
        ErrorResponse body = ErrorResponse.of("CONFLICT", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled server error [requestId={}]: ", CorrelationIdHolder.get(), ex);
        ErrorResponse body = ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred. Please contact support.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
