package com.forgecommerce.order.dto;

import com.forgecommerce.order.OrderStatus;
import com.forgecommerce.order.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
    Long id,
    OrderStatus status,
    BigDecimal total,
    List<OrderLineResponse> items,
    PaymentStatus paymentStatus,
    String paymentReference) {
  public record OrderLineResponse(
      Long productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
