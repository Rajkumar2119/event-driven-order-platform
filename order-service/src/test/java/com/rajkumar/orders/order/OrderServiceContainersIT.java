package com.rajkumar.orders.order;

import com.rajkumar.orders.order.domain.OrderRepository;
import com.rajkumar.orders.order.domain.OrderStatus;
import com.rajkumar.orders.order.outbox.OutboxRepository;
import com.rajkumar.orders.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Same flow against real PostgreSQL 16 and Apache Kafka 3.8 in Docker (Testcontainers).
 * Runs with `mvn verify` (failsafe) and in GitHub Actions; needs Docker.
 */
@SpringBootTest
@Testcontainers
class OrderServiceContainersIT {

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
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void ordersArePersistedInPostgresAndOutboxIsDrainedToKafka() {
        UUID orderId = orderService.createOrder("customer-it", new BigDecimal("12.34"), "GBP", null).order().getId();

        assertThat(orderRepository.findById(orderId)).get()
                .extracting(o -> o.getStatus()).isEqualTo(OrderStatus.PENDING);
        await().atMost(Duration.ofSeconds(30)).until(() -> outboxRepository.countByPublishedAtIsNull() == 0);
    }
}
