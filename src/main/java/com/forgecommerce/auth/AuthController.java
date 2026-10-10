package com.forgecommerce.auth;

import com.forgecommerce.auth.dto.AuthResponse;
import com.forgecommerce.auth.dto.LoginRequest;
import com.forgecommerce.auth.dto.MeResponse;
import com.forgecommerce.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req));
  }

  @GetMapping("/me")
  public ResponseEntity<MeResponse> me(Principal principal) {
    return ResponseEntity.ok(authService.me(principal.getName()));
  }
}
