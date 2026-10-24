package com.rajkumar.orders.order.messaging;

import com.rajkumar.orders.events.PaymentResultEvent;
import com.rajkumar.orders.events.Topics;
import com.rajkumar.orders.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Final step of the order/payment saga: update the order with the payment outcome. */
@Component
public class PaymentResultListener {

    private final OrderService orderService;

    public PaymentResultListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = Topics.PAYMENTS_RESULTS, groupId = "order-service")
    public void onPaymentResult(PaymentResultEvent event) {
        orderService.applyPaymentResult(event);
    }
}
