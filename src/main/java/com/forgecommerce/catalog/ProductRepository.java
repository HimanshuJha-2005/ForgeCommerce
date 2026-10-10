package com.forgecommerce.catalog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

  Page<Product> findByActiveTrue(Pageable pageable);

  Page<Product> findByActiveTrueAndCategoryId(Long categoryId, Pageable pageable);

  Page<Product> findByActiveTrueAndNameContainingIgnoreCase(String query, Pageable pageable);

  Page<Product> findByActiveTrueAndNameContainingIgnoreCaseAndCategoryId(
      String query, Long categoryId, Pageable pageable);

  /** Analytics source: one query, aggregation happens in Java streams (small catalog scale). */
  @org.springframework.data.jpa.repository.Query(
      "select p from Product p join fetch p.category where p.active = true")
  java.util.List<Product> findAllActiveWithCategory();
}
