package org.masumjia.reactcartecom.orders;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.masumjia.reactcartecom.cart.*;
import org.masumjia.reactcartecom.catalog.Product;
import org.masumjia.reactcartecom.catalog.ProductRepository;
import org.masumjia.reactcartecom.common.ApiError;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.masumjia.reactcartecom.orders.dto.OrderDtos;
import org.masumjia.reactcartecom.user.User;
import org.masumjia.reactcartecom.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Orders (Customers)")
public class OrderController {
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ProductRepository products;
    private final UserRepository users;

    public OrderController(OrderRepository orders, OrderItemRepository orderItems,
                           CartRepository carts, CartItemRepository cartItems,
                           ProductRepository products, UserRepository users) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.carts = carts;
        this.cartItems = cartItems;
        this.products = products;
        this.users = users;
    }

    @PostMapping("/orders")
    @Transactional
    @Operation(summary = "Create order from guest cart (no auth)")
    public ResponseEntity<ApiResponse<OrderDtos.OrderView>> createGuest(@Valid @RequestBody OrderDtos.CreateGuestOrderRequest req) {
        if (req == null || req.cartId() == null || req.cartId().isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "cartId is required")));
        Cart cart = carts.findById(req.cartId()).orElse(null);
        if (cart == null)
            return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        if (cartItems.findByCart_Id(cart.getId()).isEmpty())
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Cart is empty")));

        PaymentMethod pm = parsePayment(req.paymentMethod());
        if (pm == null) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Invalid paymentMethod")));
        if (pm == PaymentMethod.CARD) {
            String err = validateCard(req.card());
            if (err != null) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", err)));
        }

        // Stock reservation (atomic)
        Map<String, Integer> failed = attemptDecrement(cart.getId());
        if (!failed.isEmpty()) {
            rollbackDecrement(cart.getId(), failed);
            Map<String, String> fields = new HashMap<>();
            failed.forEach((k, v) -> fields.put(k, String.valueOf(v)));
            return ResponseEntity.status(409).body(ApiResponse.error(new ApiError("OUT_OF_STOCK", "One or more items are out of stock", fields)));
        }

        // Build order snapshot
        Order o = new Order();
        o.setId(UUID.randomUUID().toString());
        o.setStatus(OrderStatus.PENDING);
        o.setPaymentMethod(pm);
        o.setShippingAddress(formatAddress(req.name(), req.email(), req.phone(), req.address(), req.city(), req.postalCode()));
        o.setGuestEmail(req.email());
        o.setGuestName(req.name());
        o.setGuestPhone(req.phone());
        snapshotTotalsFromCart(cart, o);
        o.setCreatedAt(LocalDateTime.now());
        orders.save(o);
        // Re-read to populate DB-assigned orderNumber
        o = orders.findById(o.getId()).orElse(o);

        createOrderItemsFromCart(o, cart.getId());
        orderItems.flush();

        // Clear cart after successful order (items, coupon, monetary snapshots)
        cartItems.deleteByCart_Id(cart.getId());
        cartItems.flush();
        cart.setCouponCode(null);
        cart.setSubtotal(BigDecimal.ZERO);
        cart.setDiscountAmount(BigDecimal.ZERO);
        cart.setTotal(BigDecimal.ZERO);
        cart.setUpdatedAt(LocalDateTime.now());
        carts.save(cart);
        carts.flush();

        return ResponseEntity.status(201).body(ApiResponse.success(toView(o), Map.of("message", "Order created")));
    }

    @PostMapping("/me/orders")
    @Transactional
    @Operation(summary = "Create order from authenticated user's cart", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<OrderDtos.OrderView>> createAuthed(@Valid @RequestBody OrderDtos.CreateAuthedOrderRequest req, Authentication auth) {
        if (auth == null || auth.getName() == null)
            return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "Login required")));
        String userId = auth.getName();
        User u = users.findById(userId).orElse(null);
        if (u == null) return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "User not found")));
        Cart cart = carts.findByUserId(u.getId()).orElse(null);
        if (cart == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        if (cartItems.findByCart_Id(cart.getId()).isEmpty())
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Cart is empty")));

        PaymentMethod pm = parsePayment(req.paymentMethod());
        if (pm == null) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Invalid paymentMethod")));
        if (pm == PaymentMethod.CARD) {
            String err = validateCard(req.card());
            if (err != null) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", err)));
        }

        Map<String, Integer> failed = attemptDecrement(cart.getId());
        if (!failed.isEmpty()) {
            rollbackDecrement(cart.getId(), failed);
            Map<String, String> fields = new HashMap<>();
            failed.forEach((k, v) -> fields.put(k, String.valueOf(v)));
            return ResponseEntity.status(409).body(ApiResponse.error(new ApiError("OUT_OF_STOCK", "One or more items are out of stock", fields)));
        }

        Order o = new Order();
        o.setId(UUID.randomUUID().toString());
        o.setUser(u);
        o.setStatus(OrderStatus.PENDING);
        o.setPaymentMethod(pm);
        o.setGuestName(req.name());
        o.setGuestEmail(req.email());
        o.setGuestPhone(req.phone());
        o.setShippingAddress(formatAddress(req.name(), req.email(), req.phone(), req.address(), req.city(), req.postalCode()));
        snapshotTotalsFromCart(cart, o);
        o.setCreatedAt(LocalDateTime.now());
        orders.save(o);
        o = orders.findById(o.getId()).orElse(o);

        createOrderItemsFromCart(o, cart.getId());
        orderItems.flush();

        cartItems.deleteByCart_Id(cart.getId());
        cartItems.flush();
        cart.setCouponCode(null);
        cart.setSubtotal(BigDecimal.ZERO);
        cart.setDiscountAmount(BigDecimal.ZERO);
        cart.setTotal(BigDecimal.ZERO);
        cart.setUpdatedAt(LocalDateTime.now());
        carts.save(cart);
        carts.flush();

        return ResponseEntity.status(201).body(ApiResponse.success(toView(o), Map.of("message", "Order created")));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get order by id")
    public ResponseEntity<ApiResponse<OrderDtos.OrderView>> getOrder(@PathVariable String id, Authentication auth) {
        Order o = orders.findById(id).orElse(null);
        if (o == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Order not found")));
        // Simple visibility rule: if order has a user, only that user can view it
        if (o.getUser() != null) {
            if (auth == null || auth.getName() == null || !o.getUser().getId().equals(auth.getName()))
                return ResponseEntity.status(403).body(ApiResponse.error(new ApiError("FORBIDDEN", "Not allowed")));
        }
        return ResponseEntity.ok(ApiResponse.success(toView(o)));
    }

    @GetMapping("/orders/number/{orderNumber}")
    @Operation(summary = "Get order by order number")
    public ResponseEntity<ApiResponse<OrderDtos.OrderView>> getOrderByNumber(@PathVariable Integer orderNumber, Authentication auth) {
        Order o = orders.findByOrderNumber(orderNumber).orElse(null);
        if (o == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Order not found")));
        if (o.getUser() != null) {
            if (auth == null || auth.getName() == null || !o.getUser().getId().equals(auth.getName()))
                return ResponseEntity.status(403).body(ApiResponse.error(new ApiError("FORBIDDEN", "Not allowed")));
        }
        return ResponseEntity.ok(ApiResponse.success(toView(o)));
    }

    @GetMapping("/me/orders")
    @Operation(summary = "List my orders", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<List<OrderDtos.OrderView>>> listMine(Authentication auth) {
        if (auth == null || auth.getName() == null)
            return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "Login required")));
        List<Order> list = orders.findByUser_Id(auth.getName());
        List<OrderDtos.OrderView> out = new ArrayList<>();
        for (Order o : list) out.add(toView(o));
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/me/orders/stats")
    @Operation(summary = "Get my order statistics (totals)", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<OrderDtos.OrderStats>> myStats(Authentication auth) {
        if (auth == null || auth.getName() == null)
            return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "Login required")));
        String userId = auth.getName();
        long total = orders.countByUser_Id(userId);
        long completed = orders.countByUser_IdAndStatus(userId, OrderStatus.DELIVERED);
        java.math.BigDecimal spent = orders.sumTotalByUserAndStatus(userId, OrderStatus.DELIVERED);
        return ResponseEntity.ok(ApiResponse.success(new OrderDtos.OrderStats(total, completed, spent)));
    }

    @PatchMapping("/me/orders/{id}/cancel")
    @Transactional
    @Operation(summary = "Cancel my order within 12 hours of creation", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<OrderDtos.OrderView>> cancelMyOrder(@PathVariable String id, Authentication auth) {
        if (auth == null || auth.getName() == null)
            return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "Login required")));
        Order o = orders.findById(id).orElse(null);
        if (o == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Order not found")));
        if (o.getUser() == null || !o.getUser().getId().equals(auth.getName()))
            return ResponseEntity.status(403).body(ApiResponse.error(new ApiError("FORBIDDEN", "Not allowed")));
        if (o.getStatus() == OrderStatus.CANCELLED)
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Order already cancelled")));
        if (o.getStatus() == OrderStatus.DELIVERED)
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Delivered orders cannot be cancelled")));
        java.time.LocalDateTime created = (o.getCreatedAt() != null) ? o.getCreatedAt() : java.time.LocalDateTime.now();
        java.time.Duration since = java.time.Duration.between(created, java.time.LocalDateTime.now());
        if (since.toHours() >= 12)
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("CANCEL_WINDOW_EXPIRED", "Orders can only be cancelled within 12 hours")));
        o.setStatus(OrderStatus.CANCELLED);
        orders.save(o);
        return ResponseEntity.ok(ApiResponse.success(toView(o), java.util.Map.of("message", "Order cancelled")));
    }

    @PostMapping("/me/orders/{id}/cancel")
    @Transactional
    @Operation(summary = "Cancel my order within 12 hours of creation (POST alternative)", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<OrderDtos.OrderView>> cancelMyOrderPost(@PathVariable String id, Authentication auth) {
        return cancelMyOrder(id, auth);
    }

    // --- Helpers ---

    private PaymentMethod parsePayment(String s) {
        if (s == null) return null;
        String v = s.trim().toUpperCase();
        return switch (v) { case "COD" -> PaymentMethod.COD; case "CARD" -> PaymentMethod.CARD; default -> null; };
    }

    private String validateCard(OrderDtos.Card c) {
        if (c == null) return "Card details required";
        String number = c.number() == null ? "" : c.number().replaceAll("\\s+", "");
        // Exactly 16 digits
        if (!number.matches("^\\d{16}$")) return "Card number must be 16 digits";
        String expiry = c.expiry() == null ? "" : c.expiry().trim();
        // MM/YY format, in the future
        if (!expiry.matches("^(0[1-9]|1[0-2])\\/\\d{2}$")) return "Expiry must be MM/YY";
        String[] parts = expiry.split("/");
        int mm = Integer.parseInt(parts[0]);
        int yy = 2000 + Integer.parseInt(parts[1]);
        java.time.YearMonth em = java.time.YearMonth.of(yy, mm);
        if (!em.isAfter(java.time.YearMonth.now())) return "Card expiry must be in the future";
        // CVV can be any input as per requirement; no validation
        return null;
    }

    // Luhn check removed per requirement (only length + future expiry enforced)

    private Map<String,Integer> attemptDecrement(String cartId) {
        List<CartItem> list = cartItems.findByCart_Id(cartId);
        Map<String,Integer> failed = new HashMap<>();
        for (CartItem ci : list) {
            String pid = ci.getProduct().getId();
            int qty = ci.getQuantity();
            int updated = products.decrementIfAvailable(pid, qty);
            if (updated == 0) {
                Product p = products.findById(pid).orElse(null);
                int avail = p == null || p.getStock() == null ? 0 : p.getStock();
                failed.put(pid, avail);
            }
        }
        return failed;
    }

    private void rollbackDecrement(String cartId, Map<String,Integer> failed) {
        List<CartItem> list = cartItems.findByCart_Id(cartId);
        for (CartItem ci : list) {
            String pid = ci.getProduct().getId();
            if (!failed.containsKey(pid)) {
                products.increment(pid, ci.getQuantity());
            }
        }
    }

    private void snapshotTotalsFromCart(Cart cart, Order o) {
        // Use persisted snapshots on Cart; they are kept in sync by cart endpoints
        o.setSubtotal(cart.getSubtotal() == null ? BigDecimal.ZERO : cart.getSubtotal());
        o.setDiscount(cart.getDiscountAmount() == null ? BigDecimal.ZERO : cart.getDiscountAmount());
        BigDecimal total = cart.getTotal() == null ? o.getSubtotal().subtract(o.getDiscount()) : cart.getTotal();
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;
        o.setTotal(total);
        o.setCouponCode(cart.getCouponCode());
    }

    private void createOrderItemsFromCart(Order o, String cartId) {
        List<CartItem> cis = cartItems.findByCart_Id(cartId);
        for (CartItem ci : cis) {
            Product p = ci.getProduct();
            OrderItem oi = new OrderItem();
            oi.setId(UUID.randomUUID().toString());
            oi.setOrder(o);
            oi.setProductId(p.getId());
            oi.setProductNameSnapshot(p.getName());
            // store effective price at time of order (discounted if present)
            java.math.BigDecimal price = p.getDiscountedPrice() != null ? p.getDiscountedPrice() : (p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO);
            oi.setPrice(price);
            oi.setQuantity(ci.getQuantity());
            orderItems.save(oi);
        }
    }

    private String formatAddress(String name, String email, String phone, String address, String city, String postal) {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) sb.append(name).append("\n");
        if (email != null && !email.isBlank()) sb.append(email).append("\n");
        if (phone != null && !phone.isBlank()) sb.append(phone).append("\n");
        if (address != null && !address.isBlank()) sb.append(address).append("\n");
        if (city != null && !city.isBlank()) sb.append(city).append(" ");
        if (postal != null && !postal.isBlank()) sb.append(postal);
        return sb.toString();
    }

    private OrderDtos.OrderView toView(Order o) {
        List<OrderItem> list = orderItems.findByOrder_Id(o.getId());
        List<OrderDtos.OrderItemView> itemViews = new ArrayList<>();
        for (OrderItem it : list) {
            itemViews.add(new OrderDtos.OrderItemView(it.getProductId(), it.getProductNameSnapshot(), it.getPrice(), it.getQuantity()));
        }
        return new OrderDtos.OrderView(
                o.getId(),
                o.getOrderNumber(),
                formatOrderNumber(o.getOrderNumber()),
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

    private String formatOrderNumber(Integer n) {
        if (n == null) return null;
        return String.format("%03d", n);
    }
}
