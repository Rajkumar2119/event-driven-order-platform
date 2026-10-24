package com.rajkumar.orders.order.api;

import com.rajkumar.orders.order.domain.Order;
import com.rajkumar.orders.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        BigDecimal amount,
        String currency,
        OrderStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getAmount(), order.getCurrency(),
                order.getStatus(), order.getFailureReason(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
