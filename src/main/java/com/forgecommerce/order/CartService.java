package com.forgecommerce.order;

import com.forgecommerce.auth.User;
import com.forgecommerce.auth.UserRepository;
import com.forgecommerce.catalog.InventoryRepository;
import com.forgecommerce.catalog.Product;
import com.forgecommerce.catalog.ProductRepository;
import com.forgecommerce.common.exception.ResourceNotFoundException;
import com.forgecommerce.order.dto.CartItemRequest;
import com.forgecommerce.order.dto.CartResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

  private final CartRepository carts;
  private final CartItemRepository items;
  private final ProductRepository products;
  private final InventoryRepository inventory;
  private final UserRepository users;

  public CartService(
      CartRepository carts,
      CartItemRepository items,
      ProductRepository products,
      InventoryRepository inventory,
      UserRepository users) {
    this.carts = carts;
    this.items = items;
    this.products = products;
    this.inventory = inventory;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public CartResponse view(String email) {
    Cart cart = carts.findByUserId(userId(email)).orElse(null);
    if (cart == null) {
      return new CartResponse(List.of(), 0, BigDecimal.ZERO);
    }
    List<CartResponse.CartLineResponse> lines =
        items.findByCartId(cart.getId()).stream().map(this::toLine).toList();
    return new CartResponse(
        lines,
        lines.stream().mapToInt(CartResponse.CartLineResponse::quantity).sum(),
        lines.stream()
            .map(CartResponse.CartLineResponse::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  @Transactional
  public CartResponse add(String email, CartItemRequest req) {
    User user = findUser(email);
    Product product = findProduct(req.productId());
    if (!product.isActive()) {
      throw new IllegalStateException("Product is not available");
    }
    int stock = inventory.findByProductId(product.getId()).map(i -> i.getQuantity()).orElse(0);
    Cart cart = carts.findByUserId(user.getId()).orElseGet(() -> carts.save(new Cart(user)));
    int existing =
        items.findByCartIdAndProductId(cart.getId(), product.getId())
            .map(CartItem::getQuantity)
            .orElse(0);
    if (existing + req.quantity() > stock) {
      throw new InsufficientStockException(
          "Only " + stock + " unit(s) in stock, cannot add " + (existing + req.quantity()));
    }
    items.findByCartIdAndProductId(cart.getId(), product.getId())
        .ifPresentOrElse(
            line -> line.setQuantity(existing + req.quantity()),
            () -> items.save(new CartItem(cart, product, req.quantity())));
    return view(email);
  }

  @Transactional
  public void remove(String email, Long productId) {
    Cart cart = carts.findByUserId(userId(email)).orElse(null);
    if (cart == null) {
      return;
    }
    items.findByCartIdAndProductId(cart.getId(), productId).ifPresent(items::delete);
  }

  @Transactional
  public void clear(String email) {
    carts.findByUserId(userId(email)).ifPresent(c -> items.deleteByCartId(c.getId()));
  }

  private CartResponse.CartLineResponse toLine(CartItem item) {
    Product p = item.getProduct();
    BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    return new CartResponse.CartLineResponse(
        p.getId(), p.getName(), p.getPrice(), item.getQuantity(), lineTotal);
  }

  private Long userId(String email) {
    return findUser(email).getId();
  }

  private User findUser(String email) {
    return users.findByEmail(email).orElseThrow(() -> new IllegalStateException("User not found"));
  }

  private Product findProduct(Long id) {
    return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found"));
  }
}
