package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.controller.OrderController;
import com.cosmetic.ecommerce.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CheckoutAvailabilityTest {
    private final OrderService service = mock(OrderService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

    private org.springframework.test.web.servlet.MockMvc paused() {
        return MockMvcBuilders.standaloneSetup(new OrderController(service,
                new OrderPlacementService(service, orders, users, transactions, false))).build();
    }

    @Test void pausedDirectCheckoutReturns503AndRetryAfterBeforeAnyReadsOrWrites() throws Exception {
        paused().perform(post("/api/orders/direct").header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"productId\":1,\"quantity\":1,\"name\":\"Fixture\",\"phoneNumber\":\"test\",\"email\":\"fixture@example.test\",\"address\":\"Test\",\"paymentMethod\":\"COD\",\"expectedTotal\":1350}"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "60"));
        verifyNoInteractions(service, orders, users, transactions);
    }

    @Test void pausedCartCheckoutReturns503BeforeAnyReadsOrWrites() throws Exception {
        paused().perform(post("/api/orders").header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"shippingAddress\":\"Test\",\"customerPhone\":\"test\",\"cartItems\":[{\"productId\":1,\"quantity\":1}],\"expectedTotal\":1350}"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "60"));
        verifyNoInteractions(service, orders, users, transactions);
    }

    @Test void pauseDoesNotBlockReadingExistingOrders() throws Exception {
        paused().perform(get("/api/orders")).andExpect(status().isOk());
        verify(service).getUserOrders(null); verifyNoInteractions(orders, users, transactions);
    }
}
