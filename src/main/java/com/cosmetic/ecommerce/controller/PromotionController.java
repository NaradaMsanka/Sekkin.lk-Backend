package com.cosmetic.ecommerce.controller;

import com.cosmetic.ecommerce.dto.PromotionRequest;
import com.cosmetic.ecommerce.entity.Promotion;
import com.cosmetic.ecommerce.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/promotions")
@PreAuthorize("hasAuthority('ADMIN')")
public class PromotionController {
    private final PromotionService promotions;
    public PromotionController(PromotionService promotions) { this.promotions = promotions; }
    @GetMapping public List<Promotion> list() { return promotions.list(); }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Promotion create(@Valid @RequestBody PromotionRequest request) { return promotions.save(null, request); }
    @PutMapping("/{id}") public Promotion update(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) { return promotions.save(id, request); }
}
