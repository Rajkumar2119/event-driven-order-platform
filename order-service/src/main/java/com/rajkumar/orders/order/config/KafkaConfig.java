package com.rajkumar.orders.order.config;

import com.rajkumar.orders.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
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
    NewTopic paymentsResultsTopic() {
        return TopicBuilder.name(Topics.PAYMENTS_RESULTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentsResultsDltTopic() {
        return TopicBuilder.name(Topics.PAYMENTS_RESULTS_DLT).partitions(3).replicas(1).build();
    }

    /**
     * Retry a failing record 3 times, one second apart, then park it on the
     * matching ".DLT" topic so one bad message cannot block the partition.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaTemplate) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), new FixedBackOff(1000L, 3));
    }
}
