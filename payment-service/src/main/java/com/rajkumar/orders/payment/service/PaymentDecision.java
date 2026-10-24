package com.rajkumar.orders.payment.service;

import com.rajkumar.orders.events.PaymentStatus;

public record PaymentDecision(PaymentStatus status, String reason) {

    public static PaymentDecision approved() {
        return new PaymentDecision(PaymentStatus.COMPLETED, null);
    }

    public static PaymentDecision declined(String reason) {
        return new PaymentDecision(PaymentStatus.FAILED, reason);
    }
}
