package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Database එකට ඇත්තටම වෙනස්කම් නොයවා ඉන්න අපි Service එක Mock කරනවා
    @MockBean
    private OrderService orderService;

    @Test
    @WithMockUser(roles = "USER") // සාමාන්‍ය පරිශීලකයෙක්
    public void testUpdateOrderStatus_Unauthorized_Returns403() throws Exception {
        mockMvc.perform(patch("/api/orders/1/status")
                        .param("status", "SHIPPED"))
                .andExpect(status().isForbidden()); // 403 Error එක අනිවාර්යයෙන් පැමිණිය යුතුය
    }

    @Test
    @WithMockUser(roles = "ADMIN") // Admin කෙනෙක්
    public void testUpdateOrderStatus_Authorized_Success() throws Exception {
        mockMvc.perform(patch("/api/orders/1/status")
                        .param("status", "SHIPPED"))
                .andExpect(status().isOk()); // Admin කෙනෙක් නිසා 200 OK සාර්ථකව පැමිණිය යුතුය
    }
}