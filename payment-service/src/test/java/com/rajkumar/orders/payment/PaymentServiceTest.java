package com.rajkumar.orders.payment;

import com.rajkumar.orders.events.OrderCreatedEvent;
import com.rajkumar.orders.events.PaymentResultEvent;
import com.rajkumar.orders.events.PaymentStatus;
import com.rajkumar.orders.events.Topics;
import com.rajkumar.orders.payment.domain.Payment;
import com.rajkumar.orders.payment.domain.PaymentRepository;
import com.rajkumar.orders.payment.domain.ProcessedEvent;
import com.rajkumar.orders.payment.domain.ProcessedEventRepository;
import com.rajkumar.orders.payment.service.PaymentDecision;
import com.rajkumar.orders.payment.service.PaymentGateway;
import com.rajkumar.orders.payment.service.PaymentService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private PaymentGateway gateway;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, processedEventRepository, gateway, kafkaTemplate,
                new SimpleMeterRegistry());
    }

    @Test
    void newEventIsChargedRecordedAndPublished() {
        OrderCreatedEvent event = event(new BigDecimal("20.00"));
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(paymentRepository.findByOrderId(event.orderId())).thenReturn(Optional.empty());
        when(gateway.charge(event.amount(), "GBP")).thenReturn(PaymentDecision.approved());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(sent());

        boolean processed = paymentService.handle(event);

        assertThat(processed).isTrue();
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(Topics.PAYMENTS_RESULTS), eq(event.orderId().toString()), published.capture());
        PaymentResultEvent result = (PaymentResultEvent) published.getValue();
        assertThat(result.orderId()).isEqualTo(event.orderId());
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void duplicateEventIsSkipped() {
        OrderCreatedEvent event = event(new BigDecimal("20.00"));
        when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

        boolean processed = paymentService.handle(event);

        assertThat(processed).isFalse();
        verifyNoInteractions(gateway, kafkaTemplate);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void orderAlreadyPaidUnderAnotherEventIdIsNotChargedTwice() {
        OrderCreatedEvent event = event(new BigDecimal("20.00"));
        Payment earlier = new Payment(event.orderId(), event.amount(), "GBP", PaymentStatus.COMPLETED, null);
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(paymentRepository.findByOrderId(event.orderId())).thenReturn(Optional.of(earlier));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(sent());

        paymentService.handle(event);

        verifyNoInteractions(gateway);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void invalidEventIsRejectedWithoutRetrying() {
        OrderCreatedEvent event = event(new BigDecimal("-1.00"));

        assertThatThrownBy(() -> paymentService.handle(event)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway, kafkaTemplate);
    }

    private static OrderCreatedEvent event(BigDecimal amount) {
        return new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), "customer-1", amount, "GBP", Instant.now());
    }

    private static CompletableFuture<SendResult<String, Object>> sent() {
        return CompletableFuture.completedFuture(null);
    }
}
