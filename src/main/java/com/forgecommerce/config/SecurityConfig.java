package com.forgecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgecommerce.common.exception.ApiError;
import com.forgecommerce.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtFilter;
  private final ObjectMapper objectMapper;

  public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) {
    this.jwtFilter = jwtFilter;
    this.objectMapper = objectMapper;
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (req, res, authEx) -> writeError(res, 401, "Unauthorized", "Authentication required"))
                    .accessDeniedHandler(
                        (req, res, accessEx) -> writeError(res, 403, "Forbidden", "Access denied")))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/register", "/api/auth/login", "/api/health", "/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health")
                    .permitAll()
                    // Public storefront reads (browsing needs no login); the
                    // seller analytics endpoint stays authenticated.
                    .requestMatchers(HttpMethod.GET, "/api/products/analytics/**")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET, "/api/products", "/api/products/*", "/api/categories")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private void writeError(HttpServletResponse res, int status, String error, String message)
      throws java.io.IOException {
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ApiError body = new ApiError(Instant.now(), status, error, message, null);
    objectMapper.writeValue(res.getOutputStream(), body);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }
}
