package org.masumjia.reactcartecom.orders.dto;

import org.masumjia.reactcartecom.orders.OrderStatus;
import org.masumjia.reactcartecom.orders.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDtos {
    public static record CreateGuestOrderRequest(
            String cartId,
            String name,
            String email,
            String phone,
            String address,
            String city,
            String postalCode,
            String paymentMethod, // COD | CARD
            Card card
    ) {}

    public static record CreateAuthedOrderRequest(
            String name,
            String email,
            String phone,
            String address,
            String city,
            String postalCode,
            String paymentMethod,
            Card card
    ) {}

    public static record Card(String number, String expiry, String cvv) {}

    public static record OrderItemView(String productId, String name, BigDecimal price, Integer quantity) {}

    public static record CouponView(String code, BigDecimal discountAmount) {}

    public static record OrderView(
            String id,
            Integer orderNumber,
            String orderNumberFormatted,
            String userId,
            String status,
            String paymentMethod,
            String shippingAddress,
            String guestName,
            String guestEmail,
            String guestPhone,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            String couponCode,
            LocalDateTime createdAt,
            List<OrderItemView> items,
            CouponView coupon
    ) {}

    public static record OrderStats(
            long totalOrders,
            long completedOrders,
            BigDecimal totalSpent
    ) {}
}
