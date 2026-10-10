package com.forgecommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Minimal JWT issuer/parser. Only this class knows the signing key;
 * everything else works with the resulting subject/claims.
 */
@Service
public class JwtService {

  private final SecretKey key;
  private final long expirationMs;

  public JwtService(
      @Value("${app.jwt.secret}") String base64Secret,
      @Value("${app.jwt.expiration-ms}") long expirationMs) {
    if (base64Secret == null || base64Secret.isBlank()) {
      throw new IllegalStateException(
          "app.jwt.secret is not configured. Set APP_JWT_SECRET env var (Base64, 256-bit+).");
    }
    this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    this.expirationMs = expirationMs;
  }

  public String generateToken(String subject, String role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(subject)
        .claim("role", role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(expirationMs)))
        .signWith(key)
        .compact();
  }

  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}
