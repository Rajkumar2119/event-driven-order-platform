package com.rajkumar.orders.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted once an order has been stored. The {@code eventId} lets consumers
 * de-duplicate redeliveries (Kafka gives at-least-once delivery).
 */
public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {
}
