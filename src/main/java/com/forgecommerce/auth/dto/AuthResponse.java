package com.forgecommerce.auth.dto;

import com.forgecommerce.auth.Role;

public record AuthResponse(String token, String email, Role role) {}
