package org.masumjia.reactcartecom.cart.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CartDtos {
    public static record CreateCartResponse(String cartId) {}

    public static record AddItemRequest(Object productId, Integer quantity) {}
    public static record UpdateItemRequest(Integer quantity) {}
    public static record MergeRequest(String guestCartId, String strategy) {}

    public static record ItemView(
            String id,
            String productId,
            String name,
            BigDecimal price,
            BigDecimal discountedPrice,
            Integer stock,
            Integer quantity,
            BigDecimal lineTotal
    ) {}

    public static record CartView(
            String id,
            String userId,
            List<ItemView> items,
            Integer totalQuantity,
            BigDecimal subtotal,
            String appliedCouponCode,
            BigDecimal discountAmount,
            BigDecimal total,
            LocalDateTime updatedAt
    ) {}
}
