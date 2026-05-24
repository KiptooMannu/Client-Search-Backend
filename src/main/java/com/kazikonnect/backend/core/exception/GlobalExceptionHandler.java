package com.kazikonnect.backend.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for consistent error responses across all endpoints.
 * Does NOT extend ResponseEntityExceptionHandler to avoid ambiguous handler conflicts.
 * All exception types are handled explicitly.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation errors from @Valid annotation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult()
            .getFieldErrors()
            .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Validation Failed",
            "Invalid request data",
            errors
        );
    }

    /**
     * Handles RuntimeException - general errors.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Error",
            ex.getMessage()
        );
    }

    /**
     * Handles EntityNotFoundException.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> handleEntityNotFound(EntityNotFoundException ex) {
        return buildErrorResponse(
            HttpStatus.NOT_FOUND,
            "Not Found",
            ex.getMessage()
        );
    }

    /**
     * Handles IllegalArgumentException.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Invalid Input",
            ex.getMessage()
        );
    }

    /**
     * Handles UnsupportedOperationException.
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<?> handleUnsupportedOperation(UnsupportedOperationException ex) {
        return buildErrorResponse(
            HttpStatus.NOT_IMPLEMENTED,
            "Not Implemented",
            ex.getMessage()
        );
    }

    /**
     * Handles all other exceptions (fallback).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex) {
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            "An unexpected error occurred. Please try again later."
        );
    }

    /**
     * Builds a consistent error response without validation details.
     */
    private ResponseEntity<?> buildErrorResponse(
            HttpStatus status,
            String error,
            String message) {
        return buildErrorResponse(status, error, message, null);
    }

    /**
     * Builds a consistent error response with optional validation details.
     */
    private ResponseEntity<?> buildErrorResponse(
            HttpStatus status,
            String error,
            String message,
            Map<String, String> validationErrors) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);

        if (validationErrors != null && !validationErrors.isEmpty()) {
            body.put("validationErrors", validationErrors);
        }

        return new ResponseEntity<>(body, status);
    }
}

/**
 * Custom exception for when an entity is not found in the database.
 * Thrown from services or repositories when a requested record does not exist.
 */
class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}