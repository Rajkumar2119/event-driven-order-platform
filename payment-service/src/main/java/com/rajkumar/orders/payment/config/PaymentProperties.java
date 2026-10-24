package com.rajkumar.orders.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Rules for the simulated payment provider.
 *
 * @param maxAmount           payments above this amount are declined
 * @param supportedCurrencies ISO codes the provider accepts
 */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(BigDecimal maxAmount, Set<String> supportedCurrencies) {

    public PaymentProperties {
        if (maxAmount == null) {
            maxAmount = new BigDecimal("5000.00");
        }
        if (supportedCurrencies == null || supportedCurrencies.isEmpty()) {
            supportedCurrencies = Set.of("GBP", "EUR", "USD");
        }
    }
}
