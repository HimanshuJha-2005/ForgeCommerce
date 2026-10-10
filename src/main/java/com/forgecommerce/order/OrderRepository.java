package com.forgecommerce.order;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
  Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

  Page<Order> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
}
