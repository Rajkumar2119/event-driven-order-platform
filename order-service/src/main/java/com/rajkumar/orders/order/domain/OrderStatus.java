package com.rajkumar.orders.order.domain;

public enum OrderStatus {
    /** Stored and waiting for the payment outcome. */
    PENDING,
    /** Payment completed. */
    PAID,
    /** Payment failed; see {@link Order#getFailureReason()}. */
    REJECTED
}
