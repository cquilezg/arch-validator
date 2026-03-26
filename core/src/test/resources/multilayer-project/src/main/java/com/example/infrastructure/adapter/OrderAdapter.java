package com.example.infrastructure.adapter;

import com.example.application.port.OrderPort;
import com.example.domain.Order;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OrderAdapter implements OrderPort {

    private final Map<String, Order> storage = new HashMap<>();

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        return storage.values().stream()
                .filter(order -> order.getCustomerId().equals(customerId))
                .toList();
    }

    @Override
    public Order save(Order order) {
        storage.put(order.getId(), order);
        return order;
    }

    @Override
    public void deleteById(String id) {
        storage.remove(id);
    }
}
