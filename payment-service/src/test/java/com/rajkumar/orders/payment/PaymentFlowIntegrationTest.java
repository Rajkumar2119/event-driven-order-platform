package com.rajkumar.orders.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rajkumar.orders.events.OrderCreatedEvent;
import com.rajkumar.orders.events.PaymentResultEvent;
import com.rajkumar.orders.events.PaymentStatus;
import com.rajkumar.orders.events.Topics;
import com.rajkumar.orders.payment.domain.PaymentRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
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

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3,
        topics = {Topics.ORDERS_CREATED, Topics.ORDERS_CREATED_DLT, Topics.PAYMENTS_RESULTS})
@DirtiesContext
class PaymentFlowIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void orderEventProducesPaymentResult() throws Exception {
        OrderCreatedEvent event = event(new BigDecimal("42.00"), "GBP");

        try (Consumer<String, String> consumer = consumer("it-results-ok", Topics.PAYMENTS_RESULTS)) {
            kafkaTemplate.send(Topics.ORDERS_CREATED, event.orderId().toString(), event);

            PaymentResultEvent result = objectMapper.readValue(
                    pollForKey(consumer, event.orderId().toString()).value(), PaymentResultEvent.class);
            assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(paymentRepository.findByOrderId(event.orderId())).isPresent();
        }
    }

    @Test
    void paymentOverLimitIsDeclined() throws Exception {
        OrderCreatedEvent event = event(new BigDecimal("9999.00"), "GBP");

        try (Consumer<String, String> consumer = consumer("it-results-declined", Topics.PAYMENTS_RESULTS)) {
            kafkaTemplate.send(Topics.ORDERS_CREATED, event.orderId().toString(), event);

            PaymentResultEvent result = objectMapper.readValue(
                    pollForKey(consumer, event.orderId().toString()).value(), PaymentResultEvent.class);
            assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.reason()).contains("exceeds limit");
        }
    }

    @Test
    void redeliveredEventIsProcessedOnlyOnce() throws Exception {
        OrderCreatedEvent event = event(new BigDecimal("15.00"), "EUR");

        try (Consumer<String, String> consumer = consumer("it-results-dup", Topics.PAYMENTS_RESULTS)) {
            kafkaTemplate.send(Topics.ORDERS_CREATED, event.orderId().toString(), event);
            kafkaTemplate.send(Topics.ORDERS_CREATED, event.orderId().toString(), event);

            pollForKey(consumer, event.orderId().toString());
            Thread.sleep(2000);
            long resultsForOrder = 0;
            for (ConsumerRecord<String, String> r : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2))) {
                if (event.orderId().toString().equals(r.key())) {
                    resultsForOrder++;
                }
            }
            assertThat(resultsForOrder).as("no second result after the first").isZero();
            assertThat(paymentRepository.findAll().stream()
                    .filter(p -> p.getOrderId().equals(event.orderId())).count()).isEqualTo(1);
        }
    }

    @Test
    void unreadableMessageGoesToDeadLetterTopic() {
        String key = "poison-" + UUID.randomUUID();
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
        var rawTemplate = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps, new StringSerializer(), new StringSerializer()));

        try (Consumer<String, String> dlt = consumer("it-dlt", Topics.ORDERS_CREATED_DLT)) {
            rawTemplate.send(new ProducerRecord<>(Topics.ORDERS_CREATED, key, "{ this is not json"));

            ConsumerRecord<String, String> parked = pollForKey(dlt, key);
            assertThat(parked.topic()).isEqualTo(Topics.ORDERS_CREATED_DLT);
        }
    }

    private Consumer<String, String> consumer(String group, String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(group, "true", broker);
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                        .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }

    private static ConsumerRecord<String, String> pollForKey(Consumer<String, String> consumer, String key) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No record with key " + key + " within 20s");
    }

    private static OrderCreatedEvent event(BigDecimal amount, String currency) {
        return new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "customer-1", amount, currency, Instant.now());
    }
}
