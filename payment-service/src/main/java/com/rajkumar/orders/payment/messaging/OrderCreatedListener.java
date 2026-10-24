package com.rajkumar.orders.payment.messaging;

import com.rajkumar.orders.events.OrderCreatedEvent;
import com.rajkumar.orders.events.Topics;
import com.rajkumar.orders.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {

    private final PaymentService paymentService;

    public OrderCreatedListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = Topics.ORDERS_CREATED, groupId = "payment-service", concurrency = "3")
    public void onOrderCreated(OrderCreatedEvent event) {
        paymentService.handle(event);
    }
}
