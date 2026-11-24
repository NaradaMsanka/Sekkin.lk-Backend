package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.PromotionRequest;
import com.cosmetic.ecommerce.entity.Promotion;
import com.cosmetic.ecommerce.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class PromotionService {
    private final PromotionRepository promotions;
    private final ProductRepository products;
    public PromotionService(PromotionRepository promotions, ProductRepository products) { this.promotions = promotions; this.products = products; }

    @Transactional(readOnly = true)
    public List<Promotion> list() { return promotions.findAll(Sort.by(Sort.Direction.DESC, "id")); }

    @Transactional
    public Promotion save(Long id, PromotionRequest request) {
        String code = normalize(request.code());
        if (code == null || !code.matches("[A-Z0-9_-]{1,40}")) throw bad("Use 1–40 letters, numbers, hyphens or underscores for the promo code.");
        BigDecimal percentage = request.percentage();
        if (percentage == null || percentage.signum() <= 0 || percentage.compareTo(new BigDecimal("100")) > 0 || percentage.scale() > 2)
            throw bad("The offer rate must be between 0.01% and 100%, with at most two decimal places.");
        if (request.active() == null || request.productIds() == null || request.productIds().isEmpty() || request.productIds().size() > 200
                || request.productIds().stream().anyMatch(value -> value == null || value <= 0)) throw bad("Select at least one valid product.");
        if (products.findAllById(request.productIds()).size() != request.productIds().size()) throw bad("One or more selected products no longer exist. Refresh the product list.");
        Promotion promotion = id == null ? new Promotion() : promotions.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promo code not found."));
        promotions.findByCode(code).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This promo code already exists.");
        });
        promotion.setCode(code); promotion.setPercentage(percentage.setScale(2)); promotion.setActive(request.active());
        promotion.setProductIds(new LinkedHashSet<>(request.productIds()));
        try { return promotions.saveAndFlush(promotion); }
        catch (DataIntegrityViolationException duplicate) { throw new ResponseStatusException(HttpStatus.CONFLICT, "This promo code already exists."); }
    }

    @Transactional
    public OrderPricing.PromotionRule resolve(String suppliedCode) {
        String code = normalize(suppliedCode);
        if (code == null) return null;
        Promotion promotion = promotions.findForPricing(code).filter(Promotion::isActive)
                .orElseThrow(() -> bad("Promo code is invalid or inactive."));
        return new OrderPricing.PromotionRule(promotion.getCode(), promotion.getPercentage(), Set.copyOf(promotion.getProductIds()));
    }

    public static String normalize(String code) { return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
