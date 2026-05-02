package com.example.application

import com.example.domain.Order

open class CreateOrderUseCase {
    fun execute(orderId: String, amount: Double): Order {
        return Order(orderId, amount)
    }
}
