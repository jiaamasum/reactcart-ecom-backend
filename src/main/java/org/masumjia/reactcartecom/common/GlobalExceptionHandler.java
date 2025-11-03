package org.masumjia.reactcartecom.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        ApiError err = new ApiError("VALIDATION_ERROR", "Validation failed", fields);
        if (log.isDebugEnabled()) log.debug("Validation error: {}", ex.getMessage());
        return ResponseEntity.unprocessableEntity().body(ApiResponse.error(err));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        ApiError err = new ApiError("CONSTRAINT_VIOLATION", ex.getMessage());
        log.warn("Constraint violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(err));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArg(IllegalArgumentException ex) {
        ApiError err = new ApiError("BAD_REQUEST", ex.getMessage());
        if (log.isDebugEnabled()) log.debug("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(err));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        ApiError err = new ApiError("BAD_REQUEST", "Malformed JSON body");
        if (log.isDebugEnabled()) log.debug("Unreadable message: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(err));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        ApiError err = new ApiError("UNSUPPORTED_MEDIA_TYPE", "Content-Type not supported");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiResponse.error(err));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ApiError err = new ApiError("METHOD_NOT_ALLOWED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error(err));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(ResourceNotFoundException ex) {
        ApiError err = new ApiError("NOT_FOUND", ex.getMessage());
        if (log.isDebugEnabled()) log.debug("Not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(err));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        ApiError err = new ApiError("CONSTRAINT_VIOLATION", "Operation violates data constraints");
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(err));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception ex) {
        ApiError err = new ApiError("INTERNAL_ERROR", "Unexpected error");
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(err));
    }
}
