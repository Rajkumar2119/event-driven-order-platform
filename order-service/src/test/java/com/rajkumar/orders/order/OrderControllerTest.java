package com.rajkumar.orders.order;

import com.rajkumar.orders.order.api.OrderController;
import com.rajkumar.orders.order.config.SecurityConfig;
import com.rajkumar.orders.order.domain.Order;
import com.rajkumar.orders.order.service.OrderNotFoundException;
import com.rajkumar.orders.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private static RequestPostProcessor customer(String id, String... scopes) {
        SimpleGrantedAuthority[] authorities = new SimpleGrantedAuthority[scopes.length];
        for (int i = 0; i < scopes.length; i++) {
            authorities[i] = new SimpleGrantedAuthority("SCOPE_" + scopes[i]);
        }
        return jwt().jwt(j -> j.subject(id)).authorities(authorities);
    }

    @Test
    void rejectsRequestsWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.00, \"currency\": \"GBP\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCreateWithReadOnlyScope() throws Exception {
        mockMvc.perform(post("/api/v1/orders").with(customer("customer-1", "orders:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.00, \"currency\": \"GBP\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsOrderForTheAuthenticatedCustomer() throws Exception {
        Order order = Order.create("customer-1", new BigDecimal("10.00"), "GBP", null);
        when(orderService.createOrder(eq("customer-1"), any(BigDecimal.class), eq("GBP"), isNull()))
                .thenReturn(new OrderService.CreateOrderResult(order, true));

        mockMvc.perform(post("/api/v1/orders").with(customer("customer-1", "orders:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.00, \"currency\": \"GBP\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/orders/" + order.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value("customer-1"));
    }

    @Test
    void replayWithSameIdempotencyKeyReturns200() throws Exception {
        Order order = Order.create("customer-1", new BigDecimal("10.00"), "GBP", "abc-123");
        when(orderService.createOrder(eq("customer-1"), any(BigDecimal.class), eq("GBP"), eq("abc-123")))
                .thenReturn(new OrderService.CreateOrderResult(order, false));

        mockMvc.perform(post("/api/v1/orders").with(customer("customer-1", "orders:write"))
                        .header("Idempotency-Key", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.00, \"currency\": \"GBP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId().toString()));
    }

    @Test
    void rejectsInvalidAmountAndCurrency() throws Exception {
        mockMvc.perform(post("/api/v1/orders").with(customer("customer-1", "orders:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -5, \"currency\": \"pounds\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrderReturnsProblemDetail404() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.getOrder(id, "customer-1")).thenThrow(new OrderNotFoundException(id));

        mockMvc.perform(get("/api/v1/orders/{id}", id).with(customer("customer-1", "orders:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"));
    }
}
