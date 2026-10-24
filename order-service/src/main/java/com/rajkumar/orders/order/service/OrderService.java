package com.rajkumar.orders.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajkumar.orders.events.OrderCreatedEvent;
import com.rajkumar.orders.events.PaymentResultEvent;
import com.rajkumar.orders.events.PaymentStatus;
import com.rajkumar.orders.events.Topics;
import com.rajkumar.orders.order.domain.Order;
import com.rajkumar.orders.order.domain.OrderRepository;
import com.rajkumar.orders.order.outbox.OutboxEvent;
import com.rajkumar.orders.order.outbox.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Counter ordersCreated;
    private final MeterRegistry meterRegistry;

    public OrderService(OrderRepository orderRepository,
                        OutboxRepository outboxRepository,
                        ObjectMapper objectMapper,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.ordersCreated = Counter.builder("orders.created")
                .description("Orders accepted by the API")
                .register(meterRegistry);
    }

    /**
     * Stores the order and its OrderCreated event in one transaction (transactional outbox).
     * A repeated request with the same Idempotency-Key returns the original order.
     */
    @Transactional
    public CreateOrderResult createOrder(String customerId, BigDecimal amount, String currency, String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay for key {} -> order {}", idempotencyKey, existing.get().getId());
                return new CreateOrderResult(existing.get(), false);
            }
        }

        Order order = orderRepository.save(Order.create(customerId, amount, currency, idempotencyKey));

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), order.getId(), customerId, amount, currency, Instant.now());
        outboxRepository.save(new OutboxEvent(event.eventId(), order.getId().toString(), Topics.ORDERS_CREATED, toJson(event)));

        ordersCreated.increment();
        log.info("Order {} created for customer {} ({} {})", order.getId(), customerId, amount, currency);
        return new CreateOrderResult(order, true);
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId, String customerId) {
        // Another customer's order is reported as "not found" so ids cannot be probed.
        return orderRepository.findById(orderId)
                .filter(order -> order.getCustomerId().equals(customerId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public Page<Order> listOrders(String customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable);
    }

    /**
     * Applies a payment outcome. Safe to call more than once for the same event:
     * once the order has left PENDING, later deliveries are ignored.
     */
    @Transactional
    public void applyPaymentResult(PaymentResultEvent result) {
        Order order = orderRepository.findById(result.orderId()).orElse(null);
        if (order == null) {
            log.warn("Payment result {} refers to unknown order {}; ignoring", result.eventId(), result.orderId());
            return;
        }
        if (!order.isPending()) {
            log.info("Order {} is already {}; ignoring duplicate payment result {}", order.getId(), order.getStatus(), result.eventId());
            return;
        }
        if (result.status() == PaymentStatus.COMPLETED) {
            order.markPaid();
        } else {
            order.markRejected(result.reason());
        }
        meterRegistry.counter("orders.completed", "status", order.getStatus().name()).increment();
        log.info("Order {} is now {}", order.getId(), order.getStatus());
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise event", e);
        }
    }

    public record CreateOrderResult(Order order, boolean created) {
    }
}
