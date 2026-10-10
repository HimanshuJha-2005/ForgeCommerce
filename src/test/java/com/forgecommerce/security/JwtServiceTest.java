package com.forgecommerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

  private final JwtService jwtService = new JwtService(SECRET, 3_600_000);

  @Test
  void generateAndParseRoundTrip() {
    String token = jwtService.generateToken("user@example.com", "CUSTOMER");

    Claims claims = jwtService.parse(token);

    assertThat(claims.getSubject()).isEqualTo("user@example.com");
    assertThat(claims.get("role")).isEqualTo("CUSTOMER");
  }

  @Test
  void tamperedTokenIsRejected() {
    String token = jwtService.generateToken("user@example.com", "CUSTOMER");

    assertThatThrownBy(() -> jwtService.parse(token + "tampered")).isInstanceOf(RuntimeException.class);
  }

  @Test
  void missingSecretFailsFast() {
    assertThatThrownBy(() -> new JwtService("", 1000)).isInstanceOf(IllegalStateException.class);
  }
}
