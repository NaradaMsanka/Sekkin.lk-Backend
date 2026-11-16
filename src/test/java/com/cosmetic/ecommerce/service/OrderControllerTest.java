package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.cosmetic.ecommerce.config.SecurityConfig;
import com.cosmetic.ecommerce.config.JwtFilter;
import com.cosmetic.ecommerce.util.JwtUtil;
import com.cosmetic.ecommerce.repository.UserRepository;
import com.cosmetic.ecommerce.controller.OrderController;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtFilter.class})
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Database එකට ඇත්තටම වෙනස්කම් නොයවා ඉන්න අපි Service එක Mock කරනවා
    @MockitoBean private com.cosmetic.ecommerce.service.OrderPlacementService placement;
    @MockitoBean
    private OrderService orderService;
    @MockitoBean private JwtUtil jwt;
    @MockitoBean private UserRepository users;

    @Test
    @WithMockUser(authorities = "USER") // සාමාන්‍ය පරිශීලකයෙක්
    public void testUpdateOrderStatus_Unauthorized_Returns403() throws Exception {
        mockMvc.perform(patch("/api/orders/1/status")
                        .param("status", "SHIPPED"))
                .andExpect(status().isForbidden()); // 403 Error එක අනිවාර්යයෙන් පැමිණිය යුතුය
    }

    @Test
    @WithMockUser(authorities = "ADMIN") // Admin කෙනෙක්
    public void testUpdateOrderStatus_Authorized_Success() throws Exception {
        mockMvc.perform(patch("/api/orders/1/status")
                        .param("status", "SHIPPED"))
                .andExpect(status().isOk()); // Admin කෙනෙක් නිසා 200 OK සාර්ථකව පැමිණිය යුතුය
    }
}