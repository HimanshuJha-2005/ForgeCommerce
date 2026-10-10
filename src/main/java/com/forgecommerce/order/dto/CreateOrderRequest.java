package com.forgecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(@NotBlank String idempotencyKey) {}
