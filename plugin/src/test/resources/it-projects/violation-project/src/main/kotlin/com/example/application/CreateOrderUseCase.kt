package com.example.application

open class CreateOrderUseCase {
    fun execute(orderId: String, amount: Double): String {
        return "Order created: $orderId"
    }
}
