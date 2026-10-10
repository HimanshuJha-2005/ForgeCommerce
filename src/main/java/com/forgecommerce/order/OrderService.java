package com.forgecommerce.order;

import com.forgecommerce.auth.User;
import com.forgecommerce.auth.UserRepository;
import com.forgecommerce.catalog.Inventory;
import com.forgecommerce.catalog.InventoryRepository;
import com.forgecommerce.common.exception.ResourceNotFoundException;
import com.forgecommerce.order.dto.CreateOrderRequest;
import com.forgecommerce.order.dto.OrderResponse;
import com.forgecommerce.order.dto.OrderStatusRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout is ONE transaction: lock stock rows → validate → decrement → create
 * order + items + PENDING payment → clear cart. Any failure rolls everything back,
 * so money and inventory can never disagree. Pessimistic row locks mean two
 * simultaneous checkouts serialize on the stock row instead of overselling.
 */
@Service
public class OrderService {

  private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

  static {
    TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
    TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
    TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED));
    TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
    TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
    TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
  }

  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final CartRepository carts;
  private final CartItemRepository cartItems;
  private final InventoryRepository inventory;
  private final PaymentRepository payments;
  private final UserRepository users;

  public OrderService(
      OrderRepository orders,
      OrderItemRepository orderItems,
      CartRepository carts,
      CartItemRepository cartItems,
      InventoryRepository inventory,
      PaymentRepository payments,
      UserRepository users) {
    this.orders = orders;
    this.orderItems = orderItems;
    this.carts = carts;
    this.cartItems = cartItems;
    this.inventory = inventory;
    this.payments = payments;
    this.users = users;
  }

  public record Placement(OrderResponse order, boolean created) {}

  @Transactional
  public Placement placeOrder(String email, CreateOrderRequest req) {
    User user = findUser(email);
    // Idempotent retry: same key returns the original order instead of double-charging.
    var existing = orders.findByUserIdAndIdempotencyKey(user.getId(), req.idempotencyKey());
    if (existing.isPresent()) {
      return new Placement(toResponse(existing.get()), false);
    }
    Cart cart =
        carts.findByUserId(user.getId()).orElseThrow(() -> new IllegalStateException("Cart is empty"));
    List<CartItem> lines = cartItems.findByCartId(cart.getId());
    if (lines.isEmpty()) {
      throw new IllegalStateException("Cart is empty");
    }

    // Lock every stock row up front (sorted to avoid deadlocks) before touching anything.
    List<Long> productIds = lines.stream().map(l -> l.getProduct().getId()).sorted().toList();
    Map<Long, Inventory> stock = new java.util.HashMap<>();
    for (Long pid : productIds) {
      Inventory inv =
          inventory
              .findByProductIdForUpdate(pid)
              .orElseThrow(() -> new IllegalStateException("Product is out of stock"));
      stock.put(pid, inv);
    }
    for (CartItem line : lines) {
      Inventory inv = stock.get(line.getProduct().getId());
      if (inv.getQuantity() < line.getQuantity()) {
        throw new InsufficientStockException(
            "Only " + inv.getQuantity() + " unit(s) left for " + line.getProduct().getName());
      }
    }

    BigDecimal total =
        lines.stream()
            .map(l -> l.getProduct().getPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    Order order = orders.save(new Order(user, total, req.idempotencyKey(), Instant.now()));
    for (CartItem line : lines) {
      orderItems.save(
          new OrderItem(order, line.getProduct(), line.getQuantity(), line.getProduct().getPrice()));
      Inventory inv = stock.get(line.getProduct().getId());
      inv.setQuantity(inv.getQuantity() - line.getQuantity());
    }
    payments.save(
        new Payment(order, "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), total, Instant.now()));
    cartItems.deleteByCartId(cart.getId());
    return new Placement(toResponse(order), true);
  }

  @Transactional(readOnly = true)
  public Page<OrderResponse> history(String email, int page, int size) {
    User user = findUser(email);
    return orders
        .findByUserIdOrderByIdDesc(user.getId(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)))
        .map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public OrderResponse get(String email, Long id, boolean staff) {
    Order order = findOrder(id);
    if (!staff && !order.getUser().getEmail().equals(email)) {
      throw new SecurityException("Access denied");
    }
    return toResponse(order);
  }

  @Transactional
  public OrderResponse cancel(String email, Long id, boolean admin) {
    Order order = findOrder(id);
    if (!admin && !order.getUser().getEmail().equals(email)) {
      throw new SecurityException("Access denied");
    }
    if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
      throw new OrderTransitionException(
          "Cannot cancel order in " + order.getStatus() + " state");
    }
    restock(order);
    order.setStatus(OrderStatus.CANCELLED);
    return toResponse(order);
  }

  @Transactional
  public OrderResponse advanceStatus(Long id, OrderStatusRequest req) {
    Order order = findOrder(id);
    Set<OrderStatus> allowed = TRANSITIONS.get(order.getStatus());
    if (!allowed.contains(req.status())) {
      throw new OrderTransitionException(
          "Illegal transition from " + order.getStatus() + " to " + req.status());
    }
    order.setStatus(req.status());
    return toResponse(order);
  }

  /** Restores stock when an order is cancelled or its payment fails. */
  @Transactional
  public void restockForPaymentFailure(Order order) {
    restock(order);
  }

  private void restock(Order order) {
    for (OrderItem item : orderItems.findByOrderId(order.getId())) {
      Inventory inv =
          inventory
              .findByProductIdForUpdate(item.getProduct().getId())
              .orElseThrow(() -> new IllegalStateException("Inventory row missing"));
      inv.setQuantity(inv.getQuantity() + item.getQuantity());
    }
  }

  private Order findOrder(Long id) {
    return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
  }

  private User findUser(String email) {
    return users.findByEmail(email).orElseThrow(() -> new IllegalStateException("User not found"));
  }

  private OrderResponse toResponse(Order order) {
    List<OrderResponse.OrderLineResponse> lines =
        orderItems.findByOrderId(order.getId()).stream()
            .map(
                i ->
                    new OrderResponse.OrderLineResponse(
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))))
            .toList();
    var payment = payments.findByOrderId(order.getId()).orElse(null);
    return new OrderResponse(
        order.getId(),
        order.getStatus(),
        order.getTotal(),
        lines,
        payment == null ? null : payment.getStatus(),
        payment == null ? null : payment.getReference());
  }
}
