package org.masumjia.reactcartecom.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.masumjia.reactcartecom.catalog.ProductRepository;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.masumjia.reactcartecom.coupons.CouponRepository;
import org.masumjia.reactcartecom.orders.*;
import org.masumjia.reactcartecom.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Month;
import java.util.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@Tag(name = "Admin Dashboard")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final UserRepository users;
    private final ProductRepository products;
    private final CouponRepository coupons;

    public AdminDashboardController(OrderRepository orders, OrderItemRepository orderItems,
                                    UserRepository users, ProductRepository products,
                                    CouponRepository coupons) {
        this.orders = orders; this.orderItems = orderItems; this.users = users; this.products = products; this.coupons = coupons;
    }

    @GetMapping
    @Operation(summary = "Get aggregated dashboard metrics for admin")
    public ResponseEntity<ApiResponse<Map<String,Object>>> overview(@RequestParam(value = "lowStockThreshold", required = false, defaultValue = "5") Integer lowStockThreshold) {
        Map<String,Object> data = new LinkedHashMap<>();

        // Top KPIs
        BigDecimal totalRevenue = orders.sumTotalByStatus(OrderStatus.DELIVERED);
        long totalOrders = orders.count();
        long totalCustomers = users.count();
        long totalProducts = products.count();
        data.put("totalRevenue", totalRevenue);
        data.put("totalOrders", totalOrders);
        data.put("totalCustomers", totalCustomers);
        data.put("totalProducts", totalProducts);

        // Status distribution
        Map<String,Long> status = new LinkedHashMap<>();
        for (OrderStatus st : OrderStatus.values()) {
            status.put(st.name(), orders.countByStatus(st));
        }
        data.put("statusDistribution", status);

        // Revenue trend (by month for DELIVERED orders)
        List<Object[]> raw = orders.monthlyTotalsByStatus(OrderStatus.DELIVERED);
        List<Map<String,Object>> trend = new ArrayList<>();
        for (Object[] r : raw) {
            Integer y = (Integer) r[0];
            Integer m = (Integer) r[1];
            BigDecimal s = (BigDecimal) r[2];
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("year", y);
            row.put("month", m);
            row.put("label", Month.of(m).name());
            row.put("total", s);
            trend.add(row);
        }
        data.put("revenueTrend", trend);

        // Quick stats
        long completedOrders = orders.countByStatus(OrderStatus.DELIVERED);
        long pendingOrders = orders.countByStatus(OrderStatus.PENDING);
        long activeCoupons = coupons.countActiveAndNotExpired();
        long lowStockProducts = products.countByStockLessThanEqual(lowStockThreshold);
        Map<String,Object> quick = new LinkedHashMap<>();
        quick.put("completedOrders", completedOrders);
        quick.put("pendingOrders", pendingOrders);
        quick.put("activeCoupons", activeCoupons);
        quick.put("lowStockProducts", lowStockProducts);
        data.put("quickStats", quick);

        // Recent orders (last 10, by createdAt desc)
        List<Order> recent = orders.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        if (recent.size() > 10) recent = recent.subList(0, 10);
        List<Map<String,Object>> recentOut = new ArrayList<>();
        for (Order o : recent) {
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("id", o.getId());
            r.put("orderNumber", o.getOrderNumber());
            r.put("orderNumberFormatted", o.getOrderNumber() == null ? null : String.format("%03d", o.getOrderNumber()));
            r.put("createdAt", o.getCreatedAt());
            r.put("status", o.getStatus() == null ? null : o.getStatus().name());
            r.put("total", o.getTotal());
            r.put("customer", o.getUser() != null ? o.getUser().getId() : (o.getGuestName() != null ? o.getGuestName() : o.getGuestEmail()));
            r.put("userId", o.getUser() == null ? null : o.getUser().getId());
            r.put("guestName", o.getGuestName());
            r.put("guestEmail", o.getGuestEmail());
            r.put("guestPhone", o.getGuestPhone());
            recentOut.add(r);
        }
        data.put("recentOrders", recentOut);

        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
