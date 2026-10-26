package com.rajkumar.orders.events;

/** Kafka topic names used across the platform. */
public final class Topics {

    /** Suffix of every dead-letter topic: "<topic>.DLT". */
    public static final String DLT_SUFFIX = ".DLT";

    /** Published by order-service (via the outbox) when an order is accepted. */
    public static final String ORDERS_CREATED = "orders.created";

    /** Dead-letter topic for order events that payment-service could not process. */
    public static final String ORDERS_CREATED_DLT = ORDERS_CREATED + DLT_SUFFIX;

    /** Published by payment-service with the outcome of each payment attempt. */
    public static final String PAYMENTS_RESULTS = "payments.results";

    /** Dead-letter topic for payment results that order-service could not process. */
    public static final String PAYMENTS_RESULTS_DLT = PAYMENTS_RESULTS + DLT_SUFFIX;

    private Topics() {
    }
}
