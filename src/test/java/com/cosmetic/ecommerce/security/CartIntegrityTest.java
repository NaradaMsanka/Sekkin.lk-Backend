package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.entity.*;
import com.cosmetic.ecommerce.repository.*;
import com.cosmetic.ecommerce.service.CartService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CartIntegrityTest {
    @Mock CartRepository carts;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @InjectMocks CartService service;

    private Product product(Integer stock) {
        Product product = new Product(); product.setId(1L); product.setPrice(new BigDecimal("2500"));
        product.setDiscountPrice(new BigDecimal("2000")); product.setStockQuantity(stock);
        when(products.findById(1L)).thenReturn(Optional.of(product)); return product;
    }
    private Cart existing(Product product, int quantity) {
        User user = new User(); user.setEmail("cart@example.test");
        Cart cart = new Cart(); cart.setUser(user); CartItem item = new CartItem();
        item.setProduct(product); item.setQuantity(quantity); item.setPrice(1.0); cart.getItems().add(item);
        when(carts.findByUserEmail(user.getEmail())).thenReturn(Optional.of(cart));
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(user); return cart;
    }

    @Test void unknownProductNeverCreatesAStubOrCart() {
        User user = new User(); when(users.findByEmailForUpdate("cart@example.test")).thenReturn(user);
        assertThrows(ResponseStatusException.class, () -> service.addProductToCart("cart@example.test", 999L, 1));
        verify(products, never()).save(any()); verifyNoInteractions(carts);
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {0, -1})
    void invalidQuantityIsRejectedBeforeRepositoryAccess(Integer quantity) {
        assertThrows(ResponseStatusException.class, () -> service.addProductToCart("cart@example.test", 1L, quantity));
        assertThrows(ResponseStatusException.class, () -> service.updateItemQuantity("cart@example.test", 1L, quantity));
        verifyNoInteractions(carts, products, users);
    }

    @Test void combinedQuantityCannotExceedActualStock() {
        Product product = product(3); Cart cart = existing(product, 2);
        when(users.findByEmailForUpdate("cart@example.test")).thenReturn(cart.getUser());
        assertThrows(ResponseStatusException.class, () -> service.addProductToCart("cart@example.test", 1L, 2));
        assertEquals(2, cart.getItems().getFirst().getQuantity()); verify(carts, never()).save(any());
    }

    @Test void unknownStockNeverInvents100AvailableUnits() {
        Product product = product(null); existing(product, 1);
        assertThrows(ResponseStatusException.class, () -> service.updateItemQuantity("cart@example.test", 1L, 1));
        verify(carts, never()).save(any()); verify(products, never()).save(any());
    }

    @Test void validQuantityUsesTrustedSalePriceAndMerchandiseOnlySubtotal() {
        Product product = product(5); Cart cart = existing(product, 1);
        when(carts.save(cart)).thenReturn(cart);
        Cart result = service.updateItemQuantity("cart@example.test", 1L, 2);
        assertEquals(4000.0, result.getTotalPrice()); assertEquals(4000.0, result.getItems().getFirst().getPrice());
        assertEquals(5, product.getStockQuantity()); verify(products, never()).save(any());
    }
    @Test void loadingCartRefreshesStaleSubtotalAfterCatalogPriceChanges() {
        Product product = new Product(); product.setId(1L); product.setPrice(new BigDecimal("1500")); product.setStockQuantity(5);
        Cart cart = existing(product, 2); cart.setTotalPrice(1.0);
        assertEquals(3000.0, service.getCart("cart@example.test").getTotalPrice());
        product.setPrice(new BigDecimal("1700"));
        assertEquals(3400.0, service.getCart("cart@example.test").getTotalPrice());
        assertEquals(3400.0, cart.getItems().getFirst().getPrice());
    }
}

