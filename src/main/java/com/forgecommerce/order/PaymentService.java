package com.forgecommerce.order;

import com.forgecommerce.auth.User;
import com.forgecommerce.auth.UserRepository;
import com.forgecommerce.common.exception.ResourceNotFoundException;
import com.forgecommerce.order.dto.OrderResponse;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stand-in for a real gateway webhook (Stripe/Razorpay). The point is the STATE
 * MODEL, not the network call: a payment moves PENDING → SUCCESS|FAILED exactly
 * once, and the order follows (CONFIRMED, or CANCELLED + restock). The unique
 * payment reference makes double-processing detectable.
 */
@Service
public class PaymentService {

  private final PaymentRepository payments;
  private final OrderRepository orders;
  private final OrderService orderService;
  private final UserRepository users;

  public PaymentService(
      PaymentRepository payments, OrderRepository orders, OrderService orderService, UserRepository users) {
    this.payments = payments;
    this.orders = orders;
    this.orderService = orderService;
    this.users = users;
  }

  public record Simulation(String outcome) {}

  @Transactional
  public OrderResponse simulate(String email, Long orderId, Map<String, String> body) {
    Order order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    User user = users.findByEmail(email).orElseThrow(() -> new IllegalStateException("User not found"));
    boolean staff = user.getRole().name().equals("SELLER") || user.getRole().name().equals("ADMIN");
    if (!staff && !order.getUser().getEmail().equals(email)) {
      throw new SecurityException("Access denied");
    }
    Payment payment =
        payments.findByOrderId(orderId).orElseThrow(() -> new IllegalStateException("No payment for order"));
    if (payment.getStatus() != PaymentStatus.PENDING) {
      throw new OrderTransitionException(
          "Payment already " + payment.getStatus() + ", duplicate webhook ignored");
    }
    String outcome = body.getOrDefault("outcome", "SUCCESS").toUpperCase();
    if (outcome.equals("SUCCESS")) {
      payment.setStatus(PaymentStatus.SUCCESS);
      order.setStatus(OrderStatus.CONFIRMED);
    } else if (outcome.equals("FAILED")) {
      payment.setStatus(PaymentStatus.FAILED);
      orderService.restockForPaymentFailure(order);
      order.setStatus(OrderStatus.CANCELLED);
    } else {
      throw new IllegalArgumentException("outcome must be SUCCESS or FAILED");
    }
    // Reuse the read path for the response shape.
    return orderService.get(email, orderId, true);
  }
}
