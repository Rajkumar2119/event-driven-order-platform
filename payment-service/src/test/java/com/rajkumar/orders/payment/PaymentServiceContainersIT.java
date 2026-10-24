package com.rajkumar.orders.payment;

import com.rajkumar.orders.events.OrderCreatedEvent;
import com.rajkumar.orders.events.Topics;
import com.rajkumar.orders.payment.domain.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

/** Runs against real PostgreSQL and Kafka in Docker. Needs Docker; runs with `mvn verify`. */
@SpringBootTest
@Testcontainers
class PaymentServiceContainersIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void paymentIsStoredInPostgres() {
        OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "customer-it",
                new BigDecimal("30.00"), "GBP", Instant.now());

        kafkaTemplate.send(Topics.ORDERS_CREATED, event.orderId().toString(), event);

        await().atMost(Duration.ofSeconds(30))
                .until(() -> paymentRepository.findByOrderId(event.orderId()).isPresent());
    }
}
