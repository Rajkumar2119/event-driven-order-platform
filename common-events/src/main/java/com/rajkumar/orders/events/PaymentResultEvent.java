package com.rajkumar.orders.events;

import java.time.Instant;
import java.util.UUID;

/** Emitted by payment-service after it has processed an {@link OrderCreatedEvent}. */
public record PaymentResultEvent(
        UUID eventId,
        UUID orderId,
        UUID paymentId,
        PaymentStatus status,
        String reason,
        Instant occurredAt) {
}
