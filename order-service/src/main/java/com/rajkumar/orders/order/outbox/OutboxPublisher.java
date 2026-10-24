package com.rajkumar.orders.order.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajkumar.orders.events.OrderCreatedEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox table and publishes pending events to Kafka in creation order.
 * Delivery is at-least-once: if the app stops after sending but before marking a
 * row as published, the event is sent again and consumers de-duplicate by eventId.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, Object> kafkaTemplate,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        Gauge.builder("outbox.pending", outboxRepository, OutboxRepository::countByPublishedAtIsNull)
                .description("Outbox events waiting to be published")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                OrderCreatedEvent payload = objectMapper.readValue(event.getPayload(), OrderCreatedEvent.class);
                // Key = order id, so all events for one order land on the same partition (ordering per order).
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), payload).get(10, TimeUnit.SECONDS);
                event.markPublished();
            } catch (JsonProcessingException e) {
                log.error("Outbox event {} has an unreadable payload; skipping it", event.getId(), e);
                event.recordFailedAttempt();
                event.markPublished();
            } catch (Exception e) {
                // Broker unavailable or timed out: stop here and keep ordering; retry on the next poll.
                log.warn("Could not publish outbox event {} (attempt {}): {}", event.getId(), event.getAttempts() + 1, e.getMessage());
                event.recordFailedAttempt();
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }
    }
}
