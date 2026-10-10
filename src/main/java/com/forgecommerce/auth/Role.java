package com.forgecommerce.auth;

/**
 * API-level roles. Enforced server-side via Spring Security
 * (see config.SecurityConfig); the frontend must never be trusted
 * for authorization.
 */
public enum Role {
  CUSTOMER,
  SELLER,
  ADMIN
}
