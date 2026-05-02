package com.example.infrastructure

import com.example.domain.Order

class OrderRepository {
    fun getOrder(id: String): Order {
        return Order(id, 100.0)
    }
}
