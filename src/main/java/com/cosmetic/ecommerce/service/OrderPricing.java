package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.Product;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/** Shared new-order pricing. Historical orders always use their stored amounts. */
public final class OrderPricing {
    public static final BigDecimal DELIVERY_CHARGE = new BigDecimal("400.00");
    private OrderPricing() {}

    public record Line(Product product, Integer quantity) {}
    public record Totals(BigDecimal subtotal, BigDecimal productSavings, BigDecimal couponDiscount,
                         BigDecimal deliveryCharge, BigDecimal total, String couponCode) {
        public BigDecimal savings() { return productSavings.add(couponDiscount); }
    }

    public static BigDecimal unitPrice(Product product) {
        if (product == null || product.getPrice() == null || product.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("Product price is invalid");
        }
        BigDecimal price = money(product.getEffectivePrice());
        if (price.signum() <= 0) throw new IllegalArgumentException("Product price is invalid");
        return price;
    }

    public static Totals calculate(List<Line> lines, String couponCode) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Cannot place order. Cart is empty.");
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal productSavings = BigDecimal.ZERO;
        for (Line line : lines) {
            if (line == null || line.quantity() == null || line.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be a positive integer");
            }
            BigDecimal unit = unitPrice(line.product());
            BigDecimal quantity = BigDecimal.valueOf(line.quantity());
            subtotal = subtotal.add(unit.multiply(quantity));
            productSavings = productSavings.add(money(line.product().getPrice()).subtract(unit).max(BigDecimal.ZERO).multiply(quantity));
        }
        String code = couponCode == null ? null : couponCode.trim().toUpperCase(Locale.ROOT);
        BigDecimal discount = BigDecimal.ZERO;
        if ("GLOW10".equals(code) || "WELCOME10".equals(code)) {
            discount = money(subtotal.multiply(new BigDecimal("0.10")));
        } else if ("SAVE500".equals(code) && subtotal.compareTo(new BigDecimal("2500")) >= 0) {
            discount = new BigDecimal("500.00");
        }
        // Existing coupons apply to merchandise only; delivery is never discounted.
        discount = discount.min(subtotal);
        return new Totals(money(subtotal), money(productSavings), money(discount), DELIVERY_CHARGE,
                money(subtotal.subtract(discount).add(DELIVERY_CHARGE)), code);
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
