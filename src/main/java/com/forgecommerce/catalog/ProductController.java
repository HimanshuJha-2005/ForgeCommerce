package com.forgecommerce.catalog;

import com.forgecommerce.catalog.dto.CatalogSummary;
import com.forgecommerce.catalog.dto.InventoryUpdateRequest;
import com.forgecommerce.catalog.dto.PageResponse;
import com.forgecommerce.catalog.dto.ProductRequest;
import com.forgecommerce.catalog.dto.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final CatalogService catalog;

  public ProductController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public ResponseEntity<PageResponse<ProductResponse>> list(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "id,asc") String sort) {
    return ResponseEntity.ok(catalog.listProducts(query, categoryId, page, size, sort));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ProductResponse> get(@PathVariable Long id) {
    return ResponseEntity.ok(catalog.getProduct(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createProduct(req));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
  public ResponseEntity<ProductResponse> update(
      @PathVariable Long id, @Valid @RequestBody ProductRequest req) {
    return ResponseEntity.ok(catalog.updateProduct(id, req));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    catalog.deleteProduct(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/stock")
  @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
  public ResponseEntity<ProductResponse> setStock(
      @PathVariable Long id, @Valid @RequestBody InventoryUpdateRequest req) {
    return ResponseEntity.ok(catalog.setStock(id, req));
  }

  @GetMapping("/analytics/summary")
  @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
  public ResponseEntity<CatalogSummary> summary() {
    return ResponseEntity.ok(catalog.summarize());
  }
}
