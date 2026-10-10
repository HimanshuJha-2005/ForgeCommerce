package com.forgecommerce.order;

public class OrderTransitionException extends RuntimeException {
  public OrderTransitionException(String message) {
    super(message);
  }
}
