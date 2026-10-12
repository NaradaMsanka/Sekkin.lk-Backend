package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.OrderRepository;
import com.cosmetic.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartService cartService;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    public void testCreateOrder_InsufficientStock_ThrowsException() {
        // 1. පරීක්ෂණ දත්ත සැකසීම (Test Data Setup)
        String userEmail = "test@user.com";
        String address = "123 Colombo";

        User user = new User();
        user.setEmail(userEmail);

        // භාණ්ඩයේ Stock එක තියෙන්නේ 2යි
        Product product = new Product();
        product.setId(1L);
        product.setName("Serum");
        product.setStockQuantity(2);

        // හැබැයි User භාණ්ඩ 5ක් Cart එකට දාලා තියෙනවා
        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(5);

        Cart cart = new Cart();
        cart.setUser(user);
        cart.getItems().add(cartItem);

        when(cartService.getCart(userEmail)).thenReturn(cart);

        // 2. Order එක Create කරන්න හදනකොට RuntimeException එකක් එනවද කියලා පරීක්ෂා කිරීම
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.createOrder(userEmail, address);
        });

        // 3. එන Error පණිවිඩය නිවැරදි දැයි තහවුරු කිරීම
        assertEquals("Not enough stock for product: Serum", exception.getMessage());
    }

    @Test
    public void testCreateOrder_ValidCheckout_Success() {
        // 1. පරීක්ෂණ දත්ත සැකසීම
        String userEmail = "test@user.com";
        String address = "123 Colombo";

        User user = new User();
        user.setEmail(userEmail);

        Product product = new Product();
        product.setId(1L);
        product.setName("Serum");
        product.setStockQuantity(10); // ප්‍රමාණවත් තොගයක් ඇත
        product.setPrice(BigDecimal.valueOf(2000.0));

        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(2);
        cartItem.setPrice(4000.0);

        Cart cart = new Cart();
        cart.setUser(user);
        cart.getItems().add(cartItem);
        cart.setTotalPrice(4000.0);

        when(cartService.getCart(userEmail)).thenReturn(cart);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        // 2. Order එක Create කිරීම
        Order createdOrder = orderService.createOrder(userEmail, address);

        // 3. ප්‍රතිඵල තහවුරු කිරීම (Asserts)
        assertEquals(4000.0, createdOrder.getTotalAmount());
        assertEquals("PENDING", createdOrder.getStatus());
        assertEquals(1, createdOrder.getOrderItems().size());

        // Stock එක 10 සිට 8 (10 - 2) දක්වා අඩුවී ඇති දැයි තහවුරු කිරීම
        assertEquals(8, product.getStockQuantity());

        // Cart එක හිස් වූවාදැයි බැලීම
        verify(cartService, times(1)).clearCart(userEmail);
    }
}