package com.rajkumar.orders.payment.service;

import com.rajkumar.orders.payment.config.PaymentProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Simulated payment provider. In a real system this would call Stripe/Adyen/etc.
 * through an HTTP client with timeouts and a circuit breaker.
 */
@Component
public class PaymentGateway {

    private final PaymentProperties properties;

    public PaymentGateway(PaymentProperties properties) {
        this.properties = properties;
    }

    public PaymentDecision charge(BigDecimal amount, String currency) {
        if (!properties.supportedCurrencies().contains(currency)) {
            return PaymentDecision.declined("Currency " + currency + " is not supported");
        }
        if (amount.compareTo(properties.maxAmount()) > 0) {
            return PaymentDecision.declined("Amount exceeds limit of " + properties.maxAmount());
        }
        return PaymentDecision.approved();
    }
}
