package com.forgecommerce.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory")
public class Inventory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", unique = true)
  private Product product;

  @Column(nullable = false)
  private int quantity;

  /** Guards concurrent stock writes: concurrent updates fail fast instead of silently overselling. */
  @Version private long version;

  protected Inventory() {}

  public Inventory(Product product, int quantity) {
    this.product = product;
    setQuantity(quantity);
  }

  public Long getId() {
    return id;
  }

  public Product getProduct() {
    return product;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    if (quantity < 0) {
      throw new IllegalArgumentException("Stock quantity cannot be negative");
    }
    this.quantity = quantity;
  }
}
