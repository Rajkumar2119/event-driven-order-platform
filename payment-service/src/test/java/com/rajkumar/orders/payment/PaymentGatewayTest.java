package com.rajkumar.orders.payment;

import com.rajkumar.orders.events.PaymentStatus;
import com.rajkumar.orders.payment.config.PaymentProperties;
import com.rajkumar.orders.payment.service.PaymentDecision;
import com.rajkumar.orders.payment.service.PaymentGateway;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGatewayTest {

    private final PaymentGateway gateway =
            new PaymentGateway(new PaymentProperties(new BigDecimal("100.00"), Set.of("GBP", "EUR")));

    @Test
    void approvesPaymentWithinLimit() {
        assertThat(gateway.charge(new BigDecimal("100.00"), "GBP").status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void declinesPaymentOverLimit() {
        PaymentDecision decision = gateway.charge(new BigDecimal("100.01"), "GBP");

        assertThat(decision.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(decision.reason()).contains("exceeds limit");
    }

    @Test
    void declinesUnsupportedCurrency() {
        PaymentDecision decision = gateway.charge(new BigDecimal("10.00"), "JPY");

        assertThat(decision.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(decision.reason()).contains("JPY");
    }

    @Test
    void defaultsApplyWhenNotConfigured() {
        PaymentProperties defaults = new PaymentProperties(null, null);

        assertThat(defaults.maxAmount()).isEqualByComparingTo("5000.00");
        assertThat(defaults.supportedCurrencies()).containsExactlyInAnyOrder("GBP", "EUR", "USD");
    }
}
