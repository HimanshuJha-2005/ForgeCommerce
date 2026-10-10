package com.forgecommerce.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", unique = true)
  private Order order;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status = PaymentStatus.PENDING;

  /** Gateway-style reference; unique so double-processing is detectable. */
  @Column(nullable = false, unique = true, length = 60)
  private String reference;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected Payment() {}

  public Payment(Order order, String reference, BigDecimal amount, Instant createdAt) {
    this.order = order;
    this.reference = reference;
    this.amount = amount;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public Order getOrder() {
    return order;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public String getReference() {
    return reference;
  }

  public BigDecimal getAmount() {
    return amount;
  }
}
