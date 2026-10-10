package com.forgecommerce.auth;

import com.forgecommerce.auth.dto.AuthResponse;
import com.forgecommerce.auth.dto.LoginRequest;
import com.forgecommerce.auth.dto.MeResponse;
import com.forgecommerce.auth.dto.RegisterRequest;
import com.forgecommerce.security.JwtService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;

  public AuthService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      AuthenticationManager authenticationManager,
      JwtService jwtService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.authenticationManager = authenticationManager;
    this.jwtService = jwtService;
  }

  @Transactional
  public AuthResponse register(RegisterRequest req) {
    String email = req.email().trim().toLowerCase();
    if (users.existsByEmail(email)) {
      throw new IllegalStateException("Email already registered");
    }
    User user = new User(email, passwordEncoder.encode(req.password()), req.role(), Instant.now());
    users.save(user);
    log.info("User registered: role={}", req.role());
    return new AuthResponse(jwtService.generateToken(email, req.role().name()), email, req.role());
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest req) {
    String email = req.email().trim().toLowerCase();
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(email, req.password()));
    User user = users.findByEmail(email).orElseThrow(() -> new IllegalStateException("User not found"));
    return new AuthResponse(jwtService.generateToken(email, user.getRole().name()), email, user.getRole());
  }

  @Transactional(readOnly = true)
  public MeResponse me(String email) {
    User user = users.findByEmail(email).orElseThrow(() -> new IllegalStateException("User not found"));
    return new MeResponse(user.getEmail(), user.getRole());
  }
}
