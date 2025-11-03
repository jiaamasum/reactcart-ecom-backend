package org.masumjia.reactcartecom.orders;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.masumjia.reactcartecom.common.ApiError;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.masumjia.reactcartecom.orders.dto.OrderDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

@RestController
@RequestMapping("/api/admin/orders")
@Tag(name = "Orders (Admin)")
@SecurityRequirement(name = "bearerAuth")
public class OrderAdminController {
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;

    public OrderAdminController(OrderRepository orders, OrderItemRepository orderItems) {
        this.orders = orders;
        this.orderItems = orderItems;
    }

    @GetMapping
    @Operation(summary = "List orders with optional filters (ADMIN)")
    public ResponseEntity<ApiResponse<java.util.List<OrderDtos.OrderView>>> listAll(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "minTotal", required = false) java.math.BigDecimal minTotal,
            @RequestParam(value = "maxTotal", required = false) java.math.BigDecimal maxTotal,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size,
            @RequestParam(value = "sort", required = false, defaultValue = "createdAt,DESC") String sort
    ) {
        // Build sort
        String[] sp = sort.split(",");
        org.springframework.data.domain.Sort srt = org.springframework.data.domain.Sort.by(
                (sp.length > 0 ? sp[0] : "createdAt")
        ).descending();
        if (sp.length > 1 && "ASC".equalsIgnoreCase(sp[1])) {
            srt = org.springframework.data.domain.Sort.by(sp[0]).ascending();
        }
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, srt);

        // Build specification
        org.springframework.data.jpa.domain.Specification<Order> spec = (root, query, cb) -> cb.conjunction();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                OrderStatus st = OrderStatus.valueOf(status.trim().toUpperCase());
                spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), st));
            } catch (Exception ignored) {}
        }
        if (minTotal != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("total"), minTotal));
        }
        if (maxTotal != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("total"), maxTotal));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> {
                var userJoin = root.join("user", JoinType.LEFT);
                Predicate p = cb.or(
                        cb.like(cb.lower(root.get("guestName")), like),
                        cb.like(cb.lower(root.get("guestEmail")), like),
                        cb.like(cb.lower(root.get("couponCode")), like),
                        cb.like(cb.lower(userJoin.get("id")), like)
                );
                try {
                    Integer n = Integer.parseInt(search.trim());
                    p = cb.or(p, cb.equal(root.get("orderNumber"), n));
                } catch (Exception ignored) {}
                return p;
            });
        }

        var pageData = orders.findAll(spec, pageable);
        java.util.List<OrderDtos.OrderView> out = new java.util.ArrayList<>();
        for (Order o : pageData.getContent()) out.add(toView(o));
        java.util.Map<String,Object> meta = new java.util.LinkedHashMap<>();
        meta.put("total", pageData.getTotalElements());
        meta.put("page", page);
        meta.put("size", size);
        meta.put("totalPages", pageData.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(out, meta));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details by id (ADMIN)")
    public ResponseEntity<ApiResponse<OrderDtos.OrderView>> get(@PathVariable String id) {
        Order o = orders.findById(id).orElse(null);
        if (o == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Order not found")));
        return ResponseEntity.ok(ApiResponse.success(toView(o)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status (ADMIN)")
    public ResponseEntity<ApiResponse<Object>> updateStatus(@PathVariable String id, @RequestBody java.util.Map<String,String> body) {
        Order o = orders.findById(id).orElse(null);
        if (o == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Order not found")));
        String status = body == null ? null : body.get("status");
        if (status == null) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "status required")));
        OrderStatus st;
        try { st = OrderStatus.valueOf(status.trim().toUpperCase()); } catch (Exception e) { return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "invalid status"))); }
        o.setStatus(st);
        orders.save(o);
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("id", o.getId(), "status", o.getStatus().name())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete order (ADMIN)")
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable String id) {
        Order o = orders.findById(id).orElse(null);
        if (o == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Order not found")));
        orders.delete(o);
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("id", id), java.util.Map.of("message", "Order deleted")));
    }

    private OrderDtos.OrderView toView(Order o) {
        java.util.List<OrderItem> list = orderItems.findByOrder_Id(o.getId());
        java.util.List<OrderDtos.OrderItemView> itemViews = new java.util.ArrayList<>();
        for (OrderItem it : list) {
            itemViews.add(new OrderDtos.OrderItemView(it.getProductId(), it.getProductNameSnapshot(), it.getPrice(), it.getQuantity()));
        }
        return new OrderDtos.OrderView(
                o.getId(),
                o.getOrderNumber(),
                String.format("%03d", o.getOrderNumber() == null ? 0 : o.getOrderNumber()),
                o.getUser() == null ? null : o.getUser().getId(),
                o.getStatus() == null ? null : o.getStatus().name(),
                o.getPaymentMethod() == null ? null : o.getPaymentMethod().name(),
                o.getShippingAddress(),
                o.getGuestName(),
                o.getGuestEmail(),
                o.getGuestPhone(),
                o.getSubtotal(),
                o.getDiscount(),
                o.getTotal(),
                o.getCouponCode(),
                o.getCreatedAt(),
                itemViews,
                new OrderDtos.CouponView(o.getCouponCode(), o.getDiscount())
        );
    }
}
