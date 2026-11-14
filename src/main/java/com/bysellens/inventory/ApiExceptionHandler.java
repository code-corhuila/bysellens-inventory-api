package com.bysellens.inventory;

import java.util.List;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InventoryException.class)
    ResponseEntity<ApiError> inventory(InventoryException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiError(
                exception.getCode(), exception.getMessage(), exception.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        List<ApiError.FieldDetail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::detail)
                .toList();
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_ERROR", "Invalid request fields or bounds.", details));
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    ResponseEntity<ApiError> malformed(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_ERROR", "Invalid request JSON, fields, bounds or parameters.", null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraintViolation(ConstraintViolationException exception) {
        List<ApiError.FieldDetail> details = exception.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldDetail(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_ERROR", "Invalid request fields or bounds.", details));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicateCode(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "PRODUCT_CODE_EXISTS", "A product with this code already exists.", null));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> unavailable(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(
                "SERVICE_UNAVAILABLE", "Inventory persistence is unavailable.", null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        return ResponseEntity.internalServerError().body(new ApiError(
                "INTERNAL_ERROR", "Unexpected internal error.", null));
    }

    private ApiError.FieldDetail detail(FieldError error) {
        return new ApiError.FieldDetail(error.getField(), error.getDefaultMessage());
    }
}
