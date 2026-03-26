package com.example.application.usecase;

import com.example.application.port.OrderPort;
import com.example.domain.Order;
import java.math.BigDecimal;

public class CreateOrderUseCase {

    private final OrderPort orderPort;

    public CreateOrderUseCase(OrderPort orderPort) {
        this.orderPort = orderPort;
    }

    public Order execute(String customerId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        String orderId = generateOrderId();
        Order order = new Order(orderId, customerId, amount);
        return orderPort.save(order);
    }

    private String generateOrderId() {
        return "ORD-" + System.currentTimeMillis();
    }
}
