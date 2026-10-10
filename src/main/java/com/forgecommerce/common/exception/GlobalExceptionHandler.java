package com.forgecommerce.common.exception;

import com.forgecommerce.order.InsufficientStockException;
import com.forgecommerce.order.OrderTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single place that turns exceptions into predictable JSON.
 * Stack traces are never sent to clients; unexpected errors are logged.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + " " + f.getDefaultMessage())
            .sorted()
            .findFirst()
            .orElse("Validation failed");
    return error(HttpStatus.BAD_REQUEST, message, req);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest req) {
    return error(HttpStatus.BAD_REQUEST, "Validation failed", req);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
    return error(HttpStatus.NOT_FOUND, ex.getMessage(), req);
  }

  @ExceptionHandler(OrderTransitionException.class)
  public ResponseEntity<ApiError> handleTransition(
      OrderTransitionException ex, HttpServletRequest req) {
    return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req);
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ResponseEntity<ApiError> handleStock(InsufficientStockException ex, HttpServletRequest req) {
    return error(HttpStatus.CONFLICT, ex.getMessage(), req);
  }

  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<ApiError> handleSecurity(SecurityException ex, HttpServletRequest req) {
    return error(HttpStatus.FORBIDDEN, "Access denied", req);
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest req) {
    HttpStatus status =
        ex.getMessage() != null && ex.getMessage().toLowerCase().contains("already")
            ? HttpStatus.CONFLICT
            : HttpStatus.BAD_REQUEST;
    return error(status, ex.getMessage(), req);
  }

  @ExceptionHandler({AuthenticationException.class})
  public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
    return error(HttpStatus.UNAUTHORIZED, "Invalid email or password", req);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
    return error(HttpStatus.FORBIDDEN, "Access denied", req);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
    log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req);
  }

  private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest req) {
    return ResponseEntity.status(status)
        .body(
            new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
  }
}
