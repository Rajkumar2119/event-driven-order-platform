package com.rajkumar.orders.payment.service;

import com.rajkumar.orders.events.OrderCreatedEvent;
import com.rajkumar.orders.events.PaymentResultEvent;
import com.rajkumar.orders.events.Topics;
import com.rajkumar.orders.payment.domain.Payment;
import com.rajkumar.orders.payment.domain.PaymentRepository;
import com.rajkumar.orders.payment.domain.ProcessedEvent;
import com.rajkumar.orders.payment.domain.ProcessedEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentGateway gateway;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public PaymentService(PaymentRepository paymentRepository,
                          ProcessedEventRepository processedEventRepository,
                          PaymentGateway gateway,
                          KafkaTemplate<String, Object> kafkaTemplate,
                          MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.processedEventRepository = processedEventRepository;
        this.gateway = gateway;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Processes an order exactly once from the business point of view, even though Kafka may
     * deliver the event more than once. The result is published before the transaction commits:
     * if publishing fails, the transaction rolls back and the error handler retries the event.
     *
     * @return true if the event was processed, false if it was a duplicate
     */
    @Transactional
    public boolean handle(OrderCreatedEvent event) {
        validate(event);

        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Event {} for order {} already processed; skipping duplicate", event.eventId(), event.orderId());
            meterRegistry.counter("payments.duplicates").increment();
            return false;
        }

        // Same order delivered with a new event id (e.g. outbox replay): reuse the earlier outcome.
        Payment payment = paymentRepository.findByOrderId(event.orderId()).orElseGet(() -> {
            PaymentDecision decision = gateway.charge(event.amount(), event.currency());
            return paymentRepository.save(new Payment(event.orderId(), event.amount(), event.currency(),
                    decision.status(), decision.reason()));
        });
        processedEventRepository.save(new ProcessedEvent(event.eventId()));

        PaymentResultEvent result = new PaymentResultEvent(UUID.randomUUID(), payment.getOrderId(), payment.getId(),
                payment.getStatus(), payment.getFailureReason(), Instant.now());
        publish(result);

        meterRegistry.counter("payments.processed", "status", payment.getStatus().name()).increment();
        log.info("Payment {} for order {}: {}", payment.getId(), payment.getOrderId(), payment.getStatus());
        return true;
    }

    private void publish(PaymentResultEvent result) {
        try {
            kafkaTemplate.send(Topics.PAYMENTS_RESULTS, result.orderId().toString(), result).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing payment result", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not publish payment result for order " + result.orderId(), e);
        }
    }

    private static void validate(OrderCreatedEvent event) {
        if (event.eventId() == null || event.orderId() == null || event.amount() == null || event.currency() == null) {
            throw new IllegalArgumentException("OrderCreatedEvent is missing required fields: " + event);
        }
        if (event.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("OrderCreatedEvent has a non-positive amount: " + event);
        }
    }
}
