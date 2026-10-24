package com.rajkumar.orders.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajkumar.orders.events.OrderCreatedEvent;
import com.rajkumar.orders.events.PaymentResultEvent;
import com.rajkumar.orders.events.PaymentStatus;
import com.rajkumar.orders.events.Topics;
import com.rajkumar.orders.order.domain.Order;
import com.rajkumar.orders.order.domain.OrderRepository;
import com.rajkumar.orders.order.domain.OrderStatus;
import com.rajkumar.orders.order.outbox.OutboxEvent;
import com.rajkumar.orders.order.outbox.OutboxRepository;
import com.rajkumar.orders.order.service.OrderNotFoundException;
import com.rajkumar.orders.order.service.OrderService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, outboxRepository, objectMapper, new SimpleMeterRegistry());
    }

    @Test
    void createOrderSavesOrderAndOutboxEventTogether() throws Exception {
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = orderService.createOrder("customer-1", new BigDecimal("25.50"), "GBP", "key-1");

        assertThat(result.created()).isTrue();
        ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getTopic()).isEqualTo(Topics.ORDERS_CREATED);
        assertThat(outbox.getValue().getAggregateId()).isEqualTo(result.order().getId().toString());

        OrderCreatedEvent event = objectMapper.readValue(outbox.getValue().getPayload(), OrderCreatedEvent.class);
        assertThat(event.orderId()).isEqualTo(result.order().getId());
        assertThat(event.amount()).isEqualByComparingTo("25.50");
        assertThat(event.eventId()).isEqualTo(outbox.getValue().getId());
    }

    @Test
    void repeatedIdempotencyKeyReturnsExistingOrderWithoutNewEvent() {
        Order existing = Order.create("customer-1", new BigDecimal("25.50"), "GBP", "key-1");
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        var result = orderService.createOrder("customer-1", new BigDecimal("25.50"), "GBP", "key-1");

        assertThat(result.created()).isFalse();
        assertThat(result.order()).isSameAs(existing);
        verify(orderRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void completedPaymentMarksOrderPaid() {
        Order order = Order.create("customer-1", new BigDecimal("25.50"), "GBP", null);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.applyPaymentResult(result(order.getId(), PaymentStatus.COMPLETED, null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void failedPaymentRejectsOrder() {
        Order order = Order.create("customer-1", new BigDecimal("25.50"), "GBP", null);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.applyPaymentResult(result(order.getId(), PaymentStatus.FAILED, "Amount exceeds limit"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getFailureReason()).isEqualTo("Amount exceeds limit");
    }

    @Test
    void duplicatePaymentResultIsIgnored() {
        Order order = Order.create("customer-1", new BigDecimal("25.50"), "GBP", null);
        order.markPaid();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.applyPaymentResult(result(order.getId(), PaymentStatus.FAILED, "late duplicate"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() {
        Order order = Order.create("customer-1", new BigDecimal("25.50"), "GBP", null);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(order.getId(), "customer-2"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    private static PaymentResultEvent result(UUID orderId, PaymentStatus status, String reason) {
        return new PaymentResultEvent(UUID.randomUUID(), orderId, UUID.randomUUID(), status, reason, Instant.now());
    }
}
