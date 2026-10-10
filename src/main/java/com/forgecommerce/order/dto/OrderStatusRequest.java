package com.forgecommerce.order.dto;

import com.forgecommerce.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusRequest(@NotNull OrderStatus status) {}
