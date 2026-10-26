package com.rajkumar.orders.payment.config;

import com.rajkumar.orders.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic ordersCreatedTopic() {
        return TopicBuilder.name(Topics.ORDERS_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic ordersCreatedDltTopic() {
        return TopicBuilder.name(Topics.ORDERS_CREATED_DLT).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentsResultsTopic() {
        return TopicBuilder.name(Topics.PAYMENTS_RESULTS).partitions(3).replicas(1).build();
    }

    /**
     * Transient failures (database or broker briefly down) are retried 3 times, one second apart.
     * After that, or straight away for messages that can never succeed (bad JSON, invalid data),
     * the record goes to "orders.created.DLT" for inspection and replay.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaTemplate) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                deadLetterRecoverer(kafkaTemplate), new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    /**
     * Publishes to "<topic>.DLT" on the same partition. Spring Kafka 3's default suffix is
     * "-dlt", which would not match the DLT topics this service creates and monitors.
     */
    static DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaOperations<Object, Object> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + Topics.DLT_SUFFIX, record.partition()));
    }
}
