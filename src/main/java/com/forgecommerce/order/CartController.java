package com.forgecommerce.order;

import com.forgecommerce.order.dto.CartItemRequest;
import com.forgecommerce.order.dto.CartResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

  private final CartService cartService;

  public CartController(CartService cartService) {
    this.cartService = cartService;
  }

  @GetMapping
  public ResponseEntity<CartResponse> view(Principal principal) {
    return ResponseEntity.ok(cartService.view(principal.getName()));
  }

  @PostMapping("/items")
  public ResponseEntity<CartResponse> add(
      Principal principal, @Valid @RequestBody CartItemRequest req) {
    return ResponseEntity.ok(cartService.add(principal.getName(), req));
  }

  @DeleteMapping("/items/{productId}")
  public ResponseEntity<Void> remove(Principal principal, @PathVariable Long productId) {
    cartService.remove(principal.getName(), productId);
    return ResponseEntity.noContent().build();
  }
}
