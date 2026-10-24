package com.rajkumar.orders.order;

import com.rajkumar.orders.order.domain.Order;
import com.rajkumar.orders.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void newOrderStartsPending() {
        Order order = Order.create("customer-1", new BigDecimal("10.00"), "GBP", null);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.isPending()).isTrue();
    }

    @Test
    void pendingOrderCanBePaid() {
        Order order = Order.create("customer-1", new BigDecimal("10.00"), "GBP", null);

        order.markPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void rejectedOrderKeepsTheReason() {
        Order order = Order.create("customer-1", new BigDecimal("10.00"), "GBP", null);

        order.markRejected("Card declined");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getFailureReason()).isEqualTo("Card declined");
    }

    @Test
    void orderCannotChangeStateTwice() {
        Order order = Order.create("customer-1", new BigDecimal("10.00"), "GBP", null);
        order.markPaid();

        assertThatThrownBy(() -> order.markRejected("late failure"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already PAID");
    }
}
