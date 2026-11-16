package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.controller.OrderController;
import com.cosmetic.ecommerce.dto.DirectOrderRequestDTO;
import com.cosmetic.ecommerce.entity.Order;
import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.repository.OrderRepository;
import com.cosmetic.ecommerce.repository.ProductRepository;
import com.cosmetic.ecommerce.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DirectOrderPaymentSafetyTest {
    @Mock OrderRepository orders;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock CartService carts;
    @InjectMocks OrderService service;

    private DirectOrderRequestDTO request(String method) {
        DirectOrderRequestDTO request = new DirectOrderRequestDTO();
        request.setProductId(2L); request.setQuantity(2); request.setName("Test customer");
        request.setPhoneNumber("test phone"); request.setEmail("buyer@example.test");
        request.setAddress("Test delivery address"); request.setPaymentMethod(method);
        return request;
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"CARD", "KOKO", " card ", "PAID", "BANK_TRANSFER"})
    void rejectsUnsupportedPaymentsBeforeInventoryOrOrdersAreTouched(String method) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.createDirectOrder(request(method), null));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(orders, products, users, carts);
    }

    @Test void codRemainsUnpaidAndKeepsExistingPricingAndCouponRules() {
        Product product = new Product(); product.setId(2L); product.setName("Test product");
        product.setStockQuantity(5); product.setPrice(new BigDecimal("2500")); product.setDiscountPrice(new BigDecimal("2000"));
        when(products.findById(2L)).thenReturn(Optional.of(product));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DirectOrderRequestDTO request = request(" cod "); request.setCouponCode("GLOW10");
        Order order = service.createDirectOrder(request, null);
        assertEquals("COD", order.getPaymentMethod()); assertEquals("PENDING_COD", order.getPaymentStatus());
        assertEquals("PROCESSING", order.getStatus()); assertEquals(0.0, order.getDeliveryCharge());
        assertEquals(3600.0, order.getTotalAmount()); assertEquals(3, product.getStockQuantity());
        assertEquals(2, order.getOrderItems().getFirst().getQuantity());
        verify(orders).save(order);
    }

    @Test void httpValidationRejectsOnlineMethodsAndRemovedCardOrStatusFields() throws Exception {
        OrderService mockService = mock(OrderService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(mockService)).build();
        String base = "\"productId\":2,\"name\":\"Test\",\"phoneNumber\":\"Test\",\"email\":\"buyer@example.test\",\"address\":\"Test\"";
        for (String method : new String[]{"CARD", "KOKO"}) {
            mvc.perform(post("/api/orders/direct").contentType("application/json").content("{" + base + ",\"paymentMethod\":\"" + method + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        for (String field : new String[]{"cardNumber", "cardExpiry", "cardCvc", "paymentStatus"}) {
            mvc.perform(post("/api/orders/direct").contentType("application/json").content("{" + base + ",\"paymentMethod\":\"COD\",\"" + field + "\":\"test-only-value\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(mockService);
    }

    @Test void olderCodClientsMaySendOnlyNullLegacyCardKeys() throws Exception {
        var dto = new ObjectMapper().readValue("{\"productId\":2,\"paymentMethod\":\"COD\",\"cardNumber\":null,\"cardExpiry\":null,\"cardCvc\":null}", DirectOrderRequestDTO.class);
        assertEquals("COD", dto.getPaymentMethod());
        assertEquals(2L, dto.getProductId());
    }
}
