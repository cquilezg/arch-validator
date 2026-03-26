package com.example.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class Order {

    private final String id;
    private final String customerId;
    private final BigDecimal totalAmount;
    private final Instant createdAt;
    private OrderStatus status;

    public Order(String id, String customerId, BigDecimal totalAmount) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.totalAmount = Objects.requireNonNull(totalAmount);
        this.createdAt = Instant.now();
        this.status = OrderStatus.PENDING;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        CANCELLED
    }
}
