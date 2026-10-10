package com.forgecommerce.auth.dto;

import com.forgecommerce.auth.Role;

public record MeResponse(String email, Role role) {}
