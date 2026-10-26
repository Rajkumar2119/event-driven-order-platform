package com.rajkumar.orders.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajkumar.orders.events.OrderCreatedEvent;
import com.rajkumar.orders.events.PaymentResultEvent;
import com.rajkumar.orders.events.PaymentStatus;
import com.rajkumar.orders.events.Topics;
import com.rajkumar.orders.order.domain.Order;
import com.rajkumar.orders.order.domain.OrderRepository;
import com.rajkumar.orders.order.domain.OrderStatus;
import com.rajkumar.orders.order.outbox.OutboxRepository;
import com.rajkumar.orders.order.service.OrderService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end flow inside order-service with an in-memory database and an embedded Kafka broker:
 * order saved -> outbox publishes OrderCreated -> payment result consumed -> order updated.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = {Topics.ORDERS_CREATED, Topics.PAYMENTS_RESULTS, Topics.PAYMENTS_RESULTS_DLT})
@DirtiesContext
class OrderFlowIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void orderIsPublishedThroughOutboxAndCompletedByPaymentResult() throws Exception {
        UUID orderId = orderService.createOrder("customer-1", new BigDecimal("49.99"), "GBP", "it-key-1")
                .order().getId();

        // 1. The outbox publisher sends OrderCreated to Kafka, keyed by order id.
        try (Consumer<String, String> consumer = testConsumer("it-orders-created")) {
            broker.consumeFromAnEmbeddedTopic(consumer, Topics.ORDERS_CREATED);
            ConsumerRecord<String, String> record = pollForKey(consumer, orderId.toString());
            OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
            assertThat(event.orderId()).isEqualTo(orderId);
            assertThat(event.customerId()).isEqualTo("customer-1");
            assertThat(event.amount()).isEqualByComparingTo("49.99");
        }
        await().atMost(Duration.ofSeconds(10)).until(() -> outboxRepository.countByPublishedAtIsNull() == 0);

        // 2. payment-service's answer arrives and the order becomes PAID.
        kafkaTemplate.send(Topics.PAYMENTS_RESULTS, orderId.toString(), new PaymentResultEvent(
                UUID.randomUUID(), orderId, UUID.randomUUID(), PaymentStatus.COMPLETED, null, Instant.now()));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId)).get()
                        .extracting(Order::getStatus).isEqualTo(OrderStatus.PAID));

        // 3. A late, duplicate result does not change a finished order.
        kafkaTemplate.send(Topics.PAYMENTS_RESULTS, orderId.toString(), new PaymentResultEvent(
                UUID.randomUUID(), orderId, UUID.randomUUID(), PaymentStatus.FAILED, "duplicate", Instant.now()));
        Thread.sleep(1500);
        assertThat(orderRepository.findById(orderId)).get().extracting(Order::getStatus).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void sameIdempotencyKeyCreatesOnlyOneOrder() {
        var first = orderService.createOrder("customer-2", new BigDecimal("10.00"), "GBP", "it-key-2");
        var second = orderService.createOrder("customer-2", new BigDecimal("10.00"), "GBP", "it-key-2");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.order().getId()).isEqualTo(first.order().getId());
    }

    private Consumer<String, String> testConsumer(String group) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(group, "true", broker);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
    }

    private static ConsumerRecord<String, String> pollForKey(Consumer<String, String> consumer, String key) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No record with key " + key + " within 15s");
    }
}
