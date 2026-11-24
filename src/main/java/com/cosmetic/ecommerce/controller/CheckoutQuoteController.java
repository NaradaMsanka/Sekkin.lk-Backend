package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.CartOrderRequestDTO.CartLine;
import com.cosmetic.ecommerce.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/checkout/quote")
public class CheckoutQuoteController {
    private final CheckoutQuoteService quotes;
    public CheckoutQuoteController(CheckoutQuoteService quotes) { this.quotes = quotes; }
    public record Request(@NotEmpty @Size(max = 200) List<@NotNull @Valid CartLine> items, @Size(max = 80) String couponCode) {}
    public record Quote(BigDecimal subtotal, BigDecimal couponDiscount, BigDecimal deliveryCharge, BigDecimal total,
                        String couponCode, BigDecimal couponPercentage) {}
    @PostMapping public Quote quote(@Valid @RequestBody Request request) {
        var totals = quotes.quote(request.items(), request.couponCode());
        return new Quote(totals.subtotal(), totals.couponDiscount(), totals.deliveryCharge(), totals.total(), totals.couponCode(), totals.couponPercentage());
    }
}
