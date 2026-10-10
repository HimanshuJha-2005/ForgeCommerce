package com.forgecommerce.order;

import com.forgecommerce.catalog.dto.PageResponse;
import com.forgecommerce.order.dto.CreateOrderRequest;
import com.forgecommerce.order.dto.OrderResponse;
import com.forgecommerce.order.dto.OrderStatusRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderService orderService;
  private final PaymentService paymentService;

  public OrderController(OrderService orderService, PaymentService paymentService) {
    this.orderService = orderService;
    this.paymentService = paymentService;
  }

  @PostMapping
  public ResponseEntity<OrderResponse> place(
      Principal principal, @Valid @RequestBody CreateOrderRequest req) {
    OrderService.Placement placement = orderService.placeOrder(principal.getName(), req);
    return ResponseEntity.status(placement.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(placement.order());
  }

  @GetMapping
  public ResponseEntity<PageResponse<OrderResponse>> history(
      Principal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    var result = orderService.history(principal.getName(), page, size);
    return ResponseEntity.ok(
        new PageResponse<>(
            result.getContent(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<OrderResponse> get(
      Principal principal, @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(
        orderService.get(principal.getName(), id, isStaff(authentication)));
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<OrderResponse> cancel(
      Principal principal, @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(
        orderService.cancel(principal.getName(), id, isAdmin(authentication)));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
  public ResponseEntity<OrderResponse> advance(
      @PathVariable Long id, @Valid @RequestBody OrderStatusRequest req) {
    return ResponseEntity.ok(orderService.advanceStatus(id, req));
  }

  @PostMapping("/{id}/payment/simulate")
  public ResponseEntity<OrderResponse> simulatePayment(
      Principal principal, @PathVariable Long id, @RequestBody Map<String, String> body) {
    return ResponseEntity.ok(paymentService.simulate(principal.getName(), id, body));
  }

  private static boolean isStaff(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_SELLER") || a.getAuthority().equals("ROLE_ADMIN"));
  }

  private static boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }
}
