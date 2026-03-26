package com.example.application.port;

import com.example.domain.Order;
import java.util.List;
import java.util.Optional;

public interface OrderPort {

    Optional<Order> findById(String id);

    List<Order> findByCustomerId(String customerId);

    Order save(Order order);

    void deleteById(String id);
}
