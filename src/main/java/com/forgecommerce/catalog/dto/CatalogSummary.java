package com.forgecommerce.catalog.dto;

import java.math.BigDecimal;
import java.util.Map;

public record CatalogSummary(
    long totalProducts,
    long totalStockUnits,
    BigDecimal averagePrice,
    Map<String, CategoryStats> byCategory) {

  public record CategoryStats(long products, long stockUnits, BigDecimal averagePrice) {}
}
