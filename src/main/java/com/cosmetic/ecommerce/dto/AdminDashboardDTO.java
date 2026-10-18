package com.cosmetic.ecommerce.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class AdminDashboardDTO {
    private Double totalRevenue;
    private Long totalOrders;
    private Long totalProducts;
    private Long totalUsers;
    private List<LowStockProductDTO> lowStockProducts;
    private List<RecentOrderDTO> recentOrders;
}