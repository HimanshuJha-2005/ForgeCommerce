package com.forgecommerce.order.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<CartLineResponse> items, int totalItems, BigDecimal total) {
  public record CartLineResponse(
      Long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}
}
