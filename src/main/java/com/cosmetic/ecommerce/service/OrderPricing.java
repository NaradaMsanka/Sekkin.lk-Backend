package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.Product;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/** Shared new-order pricing. Historical orders always use their stored amounts. */
public final class OrderPricing {
    public static final BigDecimal DELIVERY_CHARGE = new BigDecimal("400.00");
    private OrderPricing() {}

    public record Line(Product product, Integer quantity) {}
    public record PromotionRule(String code, BigDecimal percentage, Set<Long> productIds) {}
    public record Totals(BigDecimal subtotal, BigDecimal productSavings, BigDecimal couponDiscount,
                         BigDecimal deliveryCharge, BigDecimal total, String couponCode, BigDecimal couponPercentage) {
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

    public static Totals calculate(List<Line> lines, PromotionRule promotion) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Cannot place order. Cart is empty.");
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal productSavings = BigDecimal.ZERO;
        BigDecimal eligibleSubtotal = BigDecimal.ZERO;
        for (Line line : lines) {
            if (line == null || line.quantity() == null || line.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be a positive integer");
            }
            BigDecimal unit = unitPrice(line.product());
            BigDecimal quantity = BigDecimal.valueOf(line.quantity());
            subtotal = subtotal.add(unit.multiply(quantity));
            if (promotion != null && promotion.productIds().contains(line.product().getId())) eligibleSubtotal = eligibleSubtotal.add(unit.multiply(quantity));
            productSavings = productSavings.add(money(line.product().getPrice()).subtract(unit).max(BigDecimal.ZERO).multiply(quantity));
        }
        BigDecimal discount = BigDecimal.ZERO;
        if (promotion != null) {
            if (promotion.percentage() == null || promotion.percentage().signum() <= 0 || promotion.percentage().compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException("Promo code has an invalid offer rate.");
            if (eligibleSubtotal.signum() <= 0) throw new IllegalArgumentException("This promo code does not apply to any products in your order.");
            discount = money(eligibleSubtotal.multiply(promotion.percentage()).movePointLeft(2)).min(eligibleSubtotal);
        }
        // Promotions affect selected merchandise only. Delivery is never discounted.
        return new Totals(money(subtotal), money(productSavings), money(discount), DELIVERY_CHARGE,
                money(subtotal.subtract(discount).add(DELIVERY_CHARGE)), promotion == null ? null : promotion.code(), promotion == null ? null : promotion.percentage());
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
