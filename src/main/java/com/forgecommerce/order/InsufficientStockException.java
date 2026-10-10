package com.forgecommerce.order;

/** Valid request, but current stock state forbids it → 409, not 400. */
public class InsufficientStockException extends RuntimeException {
  public InsufficientStockException(String message) {
    super(message);
  }
}
