package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.dto.CartOrderRequestDTO.CartLine;
import com.cosmetic.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class CheckoutQuoteService {
    private final ProductRepository products;
    private final PromotionService promotions;
    public CheckoutQuoteService(ProductRepository products, PromotionService promotions) { this.products = products; this.promotions = promotions; }
    @Transactional
    public OrderPricing.Totals quote(List<CartLine> items, String code) {
        Map<Long, Long> quantities = new LinkedHashMap<>();
        for (CartLine item : items) quantities.merge(item.productId(), item.quantity().longValue(), Long::sum);
        var catalog = products.findAllById(quantities.keySet());
        if (catalog.size() != quantities.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A selected product is no longer available.");
        List<OrderPricing.Line> lines = new ArrayList<>();
        for (var product : catalog) {
            long quantity = quantities.get(product.getId());
            if (product.getStockQuantity() == null || quantity > product.getStockQuantity() || quantity > Integer.MAX_VALUE)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Not enough stock for product: " + product.getName());
            lines.add(new OrderPricing.Line(product, (int) quantity));
        }
        try { return OrderPricing.calculate(lines, promotions.resolve(code)); }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage()); }
    }
}
