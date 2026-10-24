package com.rajkumar.orders.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order aggregate. State changes go through {@link #markPaid()} and
 * {@link #markRejected(String)} so an order can only leave PENDING once.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: concurrent updates to the same order fail instead of overwriting each other. */
    @Version
    private long version;

    protected Order() {
        // for JPA
    }

    public static Order create(String customerId, BigDecimal amount, String currency, String idempotencyKey) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.amount = amount;
        order.currency = currency;
        order.idempotencyKey = idempotencyKey;
        order.status = OrderStatus.PENDING;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        return order;
    }

    public void markPaid() {
        requirePending();
        this.status = OrderStatus.PAID;
        this.updatedAt = Instant.now();
    }

    public void markRejected(String reason) {
        requirePending();
        this.status = OrderStatus.REJECTED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("Order " + id + " is already " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
