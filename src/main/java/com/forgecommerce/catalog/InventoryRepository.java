package com.forgecommerce.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
  Optional<Inventory> findByProductId(Long productId);

  /** M3 order placement locks the row so two checkouts can't both consume the last unit. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select i from Inventory i where i.product.id = :productId")
  Optional<Inventory> findByProductIdForUpdate(@org.springframework.data.repository.query.Param("productId") Long productId);
}
