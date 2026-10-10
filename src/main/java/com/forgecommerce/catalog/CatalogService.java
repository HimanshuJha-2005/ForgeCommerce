package com.forgecommerce.catalog;

import com.forgecommerce.catalog.dto.CategoryResponse;
import com.forgecommerce.catalog.dto.CatalogSummary;
import com.forgecommerce.catalog.dto.InventoryUpdateRequest;
import com.forgecommerce.catalog.dto.PageResponse;
import com.forgecommerce.catalog.dto.ProductRequest;
import com.forgecommerce.catalog.dto.ProductResponse;
import com.forgecommerce.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

  private static final Set<String> SORTABLE = Set.of("name", "price", "createdAt", "id");

  private final ProductRepository products;
  private final CategoryRepository categories;
  private final InventoryRepository inventory;

  public CatalogService(
      ProductRepository products, CategoryRepository categories, InventoryRepository inventory) {
    this.products = products;
    this.categories = categories;
    this.inventory = inventory;
  }

  @Transactional(readOnly = true)
  public PageResponse<ProductResponse> listProducts(
      String query, Long categoryId, int page, int size, String sort) {
    Pageable pageable = pageable(page, size, sort);
    Page<Product> result;
    boolean hasQuery = query != null && !query.isBlank();
    if (hasQuery && categoryId != null) {
      result = products.findByActiveTrueAndNameContainingIgnoreCaseAndCategoryId(query.trim(), categoryId, pageable);
    } else if (hasQuery) {
      result = products.findByActiveTrueAndNameContainingIgnoreCase(query.trim(), pageable);
    } else if (categoryId != null) {
      result = products.findByActiveTrueAndCategoryId(categoryId, pageable);
    } else {
      result = products.findByActiveTrue(pageable);
    }
    // Stock lookup per row is fine at catalog scale; M5 replaces with a join fetch if it shows up in traces.
    List<ProductResponse> content = result.getContent().stream().map(this::toResponse).toList();
    return new PageResponse<>(
        content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public ProductResponse getProduct(Long id) {
    return toResponse(findProduct(id));
  }

  @Transactional
  public ProductResponse createProduct(ProductRequest req) {
    Category category = findCategory(req.categoryId());
    Product product =
        new Product(req.name().trim(), req.description(), req.price(), category, Instant.now());
    product.update(product.getName(), product.getDescription(), product.getPrice(), category, req.active());
    Product saved = products.save(product);
    inventory.save(new Inventory(saved, 0));
    return toResponse(saved);
  }

  @Transactional
  public ProductResponse updateProduct(Long id, ProductRequest req) {
    Product product = findProduct(id);
    Category category = findCategory(req.categoryId());
    product.update(req.name().trim(), req.description(), req.price(), category, req.active());
    return toResponse(product);
  }

  @Transactional
  public void deleteProduct(Long id) {
    Product product = findProduct(id);
    inventory.findByProductId(id).ifPresent(inventory::delete);
    products.delete(product);
  }

  @Transactional
  public ProductResponse setStock(Long productId, InventoryUpdateRequest req) {
    Product product = findProduct(productId);
    Inventory inv =
        inventory.findByProductId(productId).orElseGet(() -> inventory.save(new Inventory(product, 0)));
    inv.setQuantity(req.quantity());
    return toResponse(product);
  }

  /**
   * Seller-facing overview computed with the Streams API: filter active, group by
   * category, reduce to counts/averages. Single fetch query above; aggregation in memory.
   */
  @Transactional(readOnly = true)
  public CatalogSummary summarize() {
    List<Product> active = products.findAllActiveWithCategory();
    Map<Long, Integer> stockByProduct =
        inventory.findAll().stream()
            .collect(Collectors.toMap(i -> i.getProduct().getId(), Inventory::getQuantity));

    Map<String, List<Product>> byCategory =
        active.stream().collect(Collectors.groupingBy(p -> p.getCategory().getName()));

    Map<String, CatalogSummary.CategoryStats> stats =
        byCategory.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> {
                      List<Product> inCategory = e.getValue();
                      long stock =
                          inCategory.stream()
                              .mapToLong(p -> stockByProduct.getOrDefault(p.getId(), 0))
                              .sum();
                      return new CatalogSummary.CategoryStats(
                          inCategory.size(), stock, average(inCategory));
                    }));

    long totalStock = stats.values().stream().mapToLong(CatalogSummary.CategoryStats::stockUnits).sum();
    return new CatalogSummary(active.size(), totalStock, average(active), stats);
  }

  private static BigDecimal average(List<Product> list) {
    if (list.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal sum = list.stream().map(Product::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
  }

  private Product findProduct(Long id) {
    return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found"));
  }

  private Category findCategory(Long id) {
    return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found"));
  }

  private ProductResponse toResponse(Product p) {
    int stock = inventory.findByProductId(p.getId()).map(Inventory::getQuantity).orElse(0);
    return new ProductResponse(
        p.getId(), p.getName(), p.getDescription(), p.getPrice(),
        p.getCategory().getId(), p.getCategory().getName(), p.isActive(), stock);
  }

  private static Pageable pageable(int page, int size, String sort) {
    int safeSize = Math.min(Math.max(size, 1), 50);
    int safePage = Math.max(page, 0);
    Sort sortObj = Sort.by("id").ascending();
    if (sort != null && !sort.isBlank()) {
      String[] parts = sort.split(",");
      String field = parts[0].trim();
      if (SORTABLE.contains(field)) {
        Sort.Direction dir =
            parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        sortObj = Sort.by(dir, field);
      }
    }
    return PageRequest.of(safePage, safeSize, sortObj);
  }
}
