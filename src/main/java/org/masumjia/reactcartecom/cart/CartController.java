package org.masumjia.reactcartecom.cart;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.masumjia.reactcartecom.cart.dto.CartDtos;
import org.masumjia.reactcartecom.catalog.Product;
import org.masumjia.reactcartecom.catalog.ProductRepository;
import org.masumjia.reactcartecom.common.ApiError;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.masumjia.reactcartecom.coupons.Coupon;
import org.masumjia.reactcartecom.coupons.CouponAssignment;
import org.masumjia.reactcartecom.user.User;
import org.masumjia.reactcartecom.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Cart")
public class CartController {
    private final CartRepository carts;
    private final CartItemRepository items;
    private final ProductRepository products;
    private final UserRepository users;
    private final org.masumjia.reactcartecom.coupons.CouponRepository couponRepo;
    private final org.masumjia.reactcartecom.coupons.CouponAssignmentRepository assignRepo;

    public CartController(CartRepository carts, CartItemRepository items, ProductRepository products, UserRepository users,
                          org.masumjia.reactcartecom.coupons.CouponRepository couponRepo,
                          org.masumjia.reactcartecom.coupons.CouponAssignmentRepository assignRepo) {
        this.carts = carts;
        this.items = items;
        this.products = products;
        this.users = users;
        this.couponRepo = couponRepo;
        this.assignRepo = assignRepo;
    }

    @PostMapping("/carts")
    @Operation(summary = "Create guest cart (returns cartId)")
    public ResponseEntity<ApiResponse<CartDtos.CreateCartResponse>> createGuestCart(
            @Parameter(hidden = true) @CookieValue(value = "cartId", required = false) String cookieCartId,
            @RequestParam(value = "cartId", required = false) String qpCartId
    ) {
        String existing = cookieCartId != null && !cookieCartId.isBlank() ? cookieCartId : (qpCartId != null && !qpCartId.isBlank() ? qpCartId : null);
        if (existing != null) {
            Cart found = carts.findById(existing).orElse(null);
            if (found != null) {
                return ResponseEntity.ok(ApiResponse.success(new CartDtos.CreateCartResponse(found.getId())));
            }
        }
        Cart c = new Cart();
        c.setId(java.util.UUID.randomUUID().toString()); // ensure 36-char UUID to match DB schema
        c.setUpdatedAt(LocalDateTime.now());
        initCartDefaults(c);
        carts.save(c);
        return ResponseEntity.status(201).body(ApiResponse.success(new CartDtos.CreateCartResponse(c.getId())));
    }

    @GetMapping("/carts/{id}")
    @Operation(summary = "Get cart by id (guest or user)")
    public ResponseEntity<ApiResponse<CartDtos.CartView>> getCart(@PathVariable String id) {
        return carts.findById(id)
                .map(c -> ResponseEntity.ok(ApiResponse.success(toView(c, true))))
                .orElse(ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found"))));
    }

    @GetMapping("/me/cart")
    @Operation(summary = "Get current user's cart (creates if missing)", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CartDtos.CartView>> getMyCart(Authentication auth) {
        if (auth == null || auth.getName() == null) return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "Login required")));
        String userId = auth.getName(); // JWT subject is userId
        User u = users.findById(userId).orElse(null);
        if (u == null) return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "User not found")));
        Cart c = carts.findByUserId(u.getId()).orElseGet(() -> {
            Cart nc = new Cart();
            nc.setId(java.util.UUID.randomUUID().toString()); // 36-char UUID
            nc.setUser(u);
            nc.setUpdatedAt(LocalDateTime.now());
            initCartDefaults(nc);
            return carts.save(nc);
        });
        return ResponseEntity.ok(ApiResponse.success(toView(c, true)));
    }

    @PostMapping("/carts/{id}/items")
    @Transactional
    @Operation(summary = "Add item to cart (increments when exists)")
    public ResponseEntity<ApiResponse<CartDtos.CartView>> addItem(@PathVariable String id, @Valid @RequestBody CartDtos.AddItemRequest req) {
        Cart c = carts.findById(id).orElse(null);
        if (c == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        String pid = normalizeId(req.productId());
        if (pid == null || pid.isBlank()) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Product id is required")));
        Product p = products.findById(pid).orElse(null);
        if (p == null) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Product not found")));
        int qty = req.quantity() == null ? 1 : Math.max(1, req.quantity());
        int stock = p.getStock() == null ? 0 : p.getStock();
        if (stock <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("OUT_OF_STOCK", "Product out of stock")));
        }
        CartItem existing = items.findByCart_IdAndProduct_Id(id, p.getId()).orElse(null);
        if (existing != null) {
            int current = existing.getQuantity() == null ? 0 : existing.getQuantity();
            int desired = current + qty;
            existing.setQuantity(Math.min(desired, stock));
            items.save(existing);
            items.flush();
        } else {
            CartItem ci = new CartItem();
            ci.setId(java.util.UUID.randomUUID().toString());
            ci.setCart(c);
            ci.setProduct(p);
            ci.setQuantity(Math.min(qty, stock));
            items.save(ci);
            items.flush();
        }
        c.setUpdatedAt(LocalDateTime.now());
        carts.save(c);
        return ResponseEntity.ok(ApiResponse.success(toView(c, true)));
    }

    @PatchMapping("/carts/{id}/items/{productId}")
    @Transactional
    @Operation(summary = "Update item quantity (0 deletes)")
    public ResponseEntity<ApiResponse<CartDtos.CartView>> updateItem(@PathVariable String id, @PathVariable String productId, @Valid @RequestBody CartDtos.UpdateItemRequest req) {
        Cart c = carts.findById(id).orElse(null);
        if (c == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        int qty = req.quantity() == null ? 1 : req.quantity();
        CartItem existing = items.findByCart_IdAndProduct_Id(id, productId).orElse(null);
        if (existing == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Item not in cart")));
        if (qty <= 0) {
            items.delete(existing);
            items.flush();
        } else {
            Product p = existing.getProduct();
            int stock = p.getStock() == null ? 0 : p.getStock();
            if (stock <= 0) {
                items.delete(existing);
                items.flush();
            } else {
                existing.setQuantity(Math.min(qty, stock));
                items.save(existing);
                items.flush();
            }
        }
        c.setUpdatedAt(LocalDateTime.now());
        carts.save(c);
        return ResponseEntity.ok(ApiResponse.success(toView(c, true)));
    }

    @DeleteMapping("/carts/{id}/items/{productId}")
    @Transactional
    @Operation(summary = "Remove item from cart")
    public ResponseEntity<ApiResponse<CartDtos.CartView>> removeItem(@PathVariable String id, @PathVariable String productId) {
        Cart c = carts.findById(id).orElse(null);
        if (c == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        items.findByCart_IdAndProduct_Id(id, productId).ifPresent(items::delete);
        items.flush();
        c.setUpdatedAt(LocalDateTime.now());
        carts.save(c);
        return ResponseEntity.ok(ApiResponse.success(toView(c, true)));
    }

    @DeleteMapping("/carts/{id}")
    @Transactional
    @Operation(summary = "Clear cart")
    public ResponseEntity<ApiResponse<CartDtos.CartView>> clear(@PathVariable String id) {
        Cart c = carts.findById(id).orElse(null);
        if (c == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        items.deleteByCart_Id(id);
        items.flush();
        c.setUpdatedAt(LocalDateTime.now());
        carts.save(c);
        return ResponseEntity.ok(ApiResponse.success(toView(c, true)));
    }

    @PostMapping("/carts/{id}/apply-coupon")
    @Transactional
    @Operation(summary = "Apply coupon code to cart")
    public ResponseEntity<ApiResponse<CartDtos.CartView>> applyCoupon(@PathVariable String id, @RequestBody java.util.Map<String, String> body) {
        Cart c = carts.findById(id).orElse(null);
        if (c == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        String code = body == null ? null : body.get("code");
        if (code == null || code.isBlank()) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Code is required")));
        Coupon coupon = couponRepo.findByCodeIgnoreCase(code.trim()).orElse(null);
        if (coupon == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Coupon not found")));
        if (!isCouponApplicableToCart(coupon, c, null)) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Coupon not applicable")));
        c.setCouponCode(coupon.getCode());
        c.setUpdatedAt(LocalDateTime.now());
        carts.save(c);
        return ResponseEntity.ok(ApiResponse.success(toView(c, true), java.util.Map.of("message", "Coupon applied")));
    }

    @DeleteMapping("/carts/{id}/coupon")
    @Transactional
    @Operation(summary = "Remove coupon from cart")
    public ResponseEntity<ApiResponse<CartDtos.CartView>> removeCoupon(@PathVariable String id) {
        Cart c = carts.findById(id).orElse(null);
        if (c == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        c.setCouponCode(null);
        c.setUpdatedAt(LocalDateTime.now());
        carts.save(c);
        return ResponseEntity.ok(ApiResponse.success(toView(c, true), java.util.Map.of("message", "Coupon removed")));
    }

    @PostMapping("/me/cart/apply-coupon")
    @Transactional
    @Operation(summary = "Apply coupon to current user's cart", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CartDtos.CartView>> applyCouponMy(@RequestBody java.util.Map<String, String> body, Authentication auth) {
        if (auth == null || auth.getName() == null) return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "Login required")));
        String userId = auth.getName(); // JWT subject is userId
        User u = users.findById(userId).orElse(null);
        if (u == null) return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "User not found")));
        Cart c = carts.findByUserId(u.getId()).orElse(null);
        if (c == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        String code = body == null ? null : body.get("code");
        if (code == null || code.isBlank()) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Code is required")));
        Coupon coupon = couponRepo.findByCodeIgnoreCase(code.trim()).orElse(null);
        if (coupon == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Coupon not found")));
        if (!isCouponApplicableToCart(coupon, c, u.getId())) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Coupon not applicable")));
        c.setCouponCode(coupon.getCode());
        c.setUpdatedAt(LocalDateTime.now());
        carts.save(c);
        return ResponseEntity.ok(ApiResponse.success(toView(c, true), java.util.Map.of("message", "Coupon applied")));
    }

    @DeleteMapping("/me/cart/coupon")
    @Transactional
    @Operation(summary = "Remove coupon from current user's cart", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CartDtos.CartView>> removeCouponMy(Authentication auth) {
        if (auth == null || auth.getName() == null) return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "Login required")));
        String userId = auth.getName(); // JWT subject is userId
        User u = users.findById(userId).orElse(null);
        if (u == null) return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "User not found")));
        Cart c = carts.findByUserId(u.getId()).orElse(null);
        if (c == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        c.setCouponCode(null);
        c.setUpdatedAt(LocalDateTime.now());
        carts.save(c);
        return ResponseEntity.ok(ApiResponse.success(toView(c, true), java.util.Map.of("message", "Coupon removed")));
    }

    @PatchMapping("/carts/{id}/summary")
    @Transactional
    @Operation(summary = "Sync guest cart summary (apply/remove coupon and recompute totals)")
    public ResponseEntity<ApiResponse<CartDtos.CartView>> syncGuestSummary(@PathVariable String id, @RequestBody(required = false) java.util.Map<String, Object> body) {
        Cart c = carts.findById(id).orElse(null);
        if (c == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        boolean hasClientSnapshot = false;
        if (body != null) {
            // accept both `code` and `couponCode` (camel or snake)
            Object rawCode = body.containsKey("code") ? body.get("code") : (body.containsKey("couponCode") ? body.get("couponCode") : body.get("coupon_code"));
            Object rawSubtotal = body.containsKey("subtotal") ? body.get("subtotal") : body.get("sub_total");
            Object rawDiscount = body.containsKey("discountAmount") ? body.get("discountAmount") : (body.containsKey("discount_amount") ? body.get("discount_amount") : body.get("discount"));
            Object rawTotal = body.get("total");

            if (rawCode != null) {
                String code = rawCode.toString();
                if (code.isBlank()) {
                    c.setCouponCode(null);
                } else {
                    Coupon coupon = couponRepo.findByCodeIgnoreCase(code.trim()).orElse(null);
                    if (coupon == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Coupon not found")));
                    if (!isCouponApplicableToCart(coupon, c, null)) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Coupon not applicable")));
                    c.setCouponCode(coupon.getCode());
                }
                hasClientSnapshot = true; // coupon came from client
            }
            java.math.BigDecimal sub = parseDecimal(rawSubtotal);
            java.math.BigDecimal dis = parseDecimal(rawDiscount);
            java.math.BigDecimal tot = parseDecimal(rawTotal);
            if (sub != null || dis != null || tot != null) {
                if (sub != null) c.setSubtotal(sub);
                if (dis != null) c.setDiscountAmount(dis);
                if (tot != null) c.setTotal(tot);
                hasClientSnapshot = true;
            }
            if (hasClientSnapshot) {
                c.setUpdatedAt(LocalDateTime.now());
                carts.save(c);
            }
        }
        CartDtos.CartView view = hasClientSnapshot ? toViewSnapshot(c) : toView(c, true);
        return ResponseEntity.ok(ApiResponse.success(view, java.util.Map.of("message", hasClientSnapshot ? "Summary synced (client)" : "Summary synced")));
    }

    @PatchMapping("/me/cart/summary")
    @Transactional
    @Operation(summary = "Sync current user's cart summary (apply/remove coupon and recompute totals)", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CartDtos.CartView>> syncMySummary(@RequestBody(required = false) java.util.Map<String, Object> body, Authentication auth) {
        if (auth == null || auth.getName() == null) return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "Login required")));
        String userId = auth.getName();
        User u = users.findById(userId).orElse(null);
        if (u == null) return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "User not found")));
        Cart c = carts.findByUserId(u.getId()).orElse(null);
        if (c == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        boolean hasClientSnapshot = false;
        if (body != null) {
            Object rawCode = body.containsKey("code") ? body.get("code") : (body.containsKey("couponCode") ? body.get("couponCode") : body.get("coupon_code"));
            Object rawSubtotal = body.containsKey("subtotal") ? body.get("subtotal") : body.get("sub_total");
            Object rawDiscount = body.containsKey("discountAmount") ? body.get("discountAmount") : (body.containsKey("discount_amount") ? body.get("discount_amount") : body.get("discount"));
            Object rawTotal = body.get("total");

            if (rawCode != null) {
                String code = rawCode.toString();
                if (code.isBlank()) {
                    c.setCouponCode(null);
                } else {
                    Coupon coupon = couponRepo.findByCodeIgnoreCase(code.trim()).orElse(null);
                    if (coupon == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Coupon not found")));
                    if (!isCouponApplicableToCart(coupon, c, u.getId())) return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Coupon not applicable")));
                    c.setCouponCode(coupon.getCode());
                }
                hasClientSnapshot = true;
            }
            java.math.BigDecimal sub = parseDecimal(rawSubtotal);
            java.math.BigDecimal dis = parseDecimal(rawDiscount);
            java.math.BigDecimal tot = parseDecimal(rawTotal);
            if (sub != null || dis != null || tot != null) {
                if (sub != null) c.setSubtotal(sub);
                if (dis != null) c.setDiscountAmount(dis);
                if (tot != null) c.setTotal(tot);
                hasClientSnapshot = true;
            }
            if (hasClientSnapshot) {
                c.setUpdatedAt(LocalDateTime.now());
                carts.save(c);
            }
        }
        CartDtos.CartView view = hasClientSnapshot ? toViewSnapshot(c) : toView(c, true);
        return ResponseEntity.ok(ApiResponse.success(view, java.util.Map.of("message", hasClientSnapshot ? "Summary synced (client)" : "Summary synced")));
    }

    private java.math.BigDecimal parseDecimal(Object raw) {
        if (raw == null) return null;
        try {
            return new java.math.BigDecimal(raw.toString()).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private CartDtos.CartView toViewSnapshot(Cart c) {
        List<CartItem> list = items.findByCart_Id(c.getId());
        List<CartDtos.ItemView> views = new ArrayList<>();
        java.math.BigDecimal calcSubtotal = java.math.BigDecimal.ZERO;
        int totalQty = 0;
        for (CartItem ci : list) {
            if (ci == null) continue;
            Product p = ci.getProduct();
            if (p == null) continue;
            java.math.BigDecimal price = safeEffectivePrice(p);
            java.math.BigDecimal line = price.multiply(java.math.BigDecimal.valueOf(ci.getQuantity() == null ? 0 : ci.getQuantity()));
            calcSubtotal = calcSubtotal.add(line);
            totalQty += ci.getQuantity() == null ? 0 : ci.getQuantity();
            views.add(new CartDtos.ItemView(
                    ci.getId(),
                    p.getId(),
                    p.getName(),
                    p.getPrice(),
                    p.getDiscountedPrice(),
                    p.getStock(),
                    ci.getQuantity(),
                    line
            ));
        }
        java.math.BigDecimal subtotal = c.getSubtotal() != null ? c.getSubtotal() : calcSubtotal;
        java.math.BigDecimal discount = c.getDiscountAmount() != null ? c.getDiscountAmount() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal total = c.getTotal() != null ? c.getTotal() : subtotal.subtract(discount);
        if (total.compareTo(java.math.BigDecimal.ZERO) < 0) total = java.math.BigDecimal.ZERO;
        String applied = c.getCouponCode();
        return new CartDtos.CartView(c.getId(), c.getUser() == null ? null : c.getUser().getId(), views, totalQty, subtotal, applied, discount, total, c.getUpdatedAt());
    }

    @PostMapping("/me/cart/merge")
    @Transactional
    @Operation(summary = "Merge a guest cart into current user's cart", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CartDtos.CartView>> merge(@Valid @RequestBody CartDtos.MergeRequest req, Authentication auth) {
        if (auth == null || auth.getName() == null) return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "Login required")));
        String userId = auth.getName(); // JWT subject is userId
        User u = users.findById(userId).orElse(null);
        if (u == null) return ResponseEntity.status(401).body(ApiResponse.error(new ApiError("UNAUTHORIZED", "User not found")));
        Cart existingUserCart = carts.findByUserId(u.getId()).orElse(null);
        Cart guest = req.guestCartId() == null ? null : carts.findById(req.guestCartId()).orElse(null);
        if (guest == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Guest cart not found")));
        if (existingUserCart == null) {
            guest.setUser(u);
            guest.setUpdatedAt(LocalDateTime.now());
            initCartDefaults(guest);
            carts.save(guest);
            return ResponseEntity.ok(ApiResponse.success(toView(guest, true), Map.of("message", "Cart claimed")));
        }
        Cart target = existingUserCart;
        Map<String, CartItem> byProd = new HashMap<>();
        for (CartItem ci : items.findByCart_Id(target.getId())) byProd.put(ci.getProduct().getId(), ci);
        for (CartItem gi : items.findByCart_Id(guest.getId())) {
            int gQty = gi.getQuantity();
            Product gp = gi.getProduct();
            int stock = gp.getStock() == null ? 0 : gp.getStock();
            CartItem dest = byProd.get(gp.getId());
            if (dest != null && (req.strategy() == null || req.strategy().equalsIgnoreCase("sum"))) {
                int desired = dest.getQuantity() + gQty;
                dest.setQuantity(Math.min(desired, stock));
                items.save(dest);
                items.flush();
            } else if (dest != null && req.strategy() != null && req.strategy().equalsIgnoreCase("replace")) {
                dest.setQuantity(Math.min(gQty, stock));
                items.save(dest);
                items.flush();
            } else if (stock > 0) {
                CartItem nc = new CartItem();
                nc.setId(java.util.UUID.randomUUID().toString());
                nc.setCart(target); nc.setProduct(gp); nc.setQuantity(Math.min(gQty, stock));
                items.save(nc);
                items.flush();
            }
        }
        items.deleteByCart_Id(guest.getId());
        carts.delete(guest);
        target.setUpdatedAt(LocalDateTime.now());
        carts.save(target);
        return ResponseEntity.ok(ApiResponse.success(toView(target, true), Map.of("message", "Cart merged")));
    }

    @PostMapping("/carts/{id}/checkout")
    @Transactional
    @Operation(summary = "Attempt to decrement stock for all items atomically; fails if any item insufficient")
    public ResponseEntity<ApiResponse<Object>> checkout(@PathVariable String id) {
        Cart c = carts.findById(id).orElse(null);
        if (c == null) return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Cart not found")));
        List<CartItem> list = items.findByCart_Id(id);
        Map<String, Integer> failed = new HashMap<>();
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
        if (!failed.isEmpty()) {
            for (CartItem ci : list) {
                String pid = ci.getProduct().getId();
                if (!failed.containsKey(pid)) {
                    products.increment(pid, ci.getQuantity());
                }
            }
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            Map<String, String> fields = new HashMap<>();
            for (Map.Entry<String, Integer> e : failed.entrySet()) {
                fields.put(e.getKey(), String.valueOf(e.getValue()));
            }
            return ResponseEntity.status(409).body(ApiResponse.error(new ApiError("OUT_OF_STOCK", "One or more items are out of stock", fields)));
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("cartId", c.getId()), Map.of("message", "Stock reserved")));
    }

    private CartDtos.CartView toView(Cart c, boolean computeCoupon) {
        List<CartItem> list = items.findByCart_Id(c.getId());
        List<CartDtos.ItemView> views = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalQty = 0;
        for (CartItem ci : list) {
            if (ci == null) continue;
            Product p = ci.getProduct();
            if (p == null) continue;
            BigDecimal price = safeEffectivePrice(p);
            BigDecimal line = price.multiply(BigDecimal.valueOf(ci.getQuantity() == null ? 0 : ci.getQuantity()));
            subtotal = subtotal.add(line);
            totalQty += ci.getQuantity() == null ? 0 : ci.getQuantity();
            views.add(new CartDtos.ItemView(
                    ci.getId(),
                    p.getId(),
                    p.getName(),
                    p.getPrice(),
                    p.getDiscountedPrice(),
                    p.getStock(),
                    ci.getQuantity(),
                    line
            ));
        }
        BigDecimal discount = BigDecimal.ZERO;
        String applied = c.getCouponCode();
        if (computeCoupon && applied != null && !applied.isBlank()) {
            Coupon coupon = couponRepo.findByCodeIgnoreCase(applied).orElse(null);
            if (coupon != null) {
                String userId = c.getUser() == null ? null : c.getUser().getId();
                if (isCouponApplicableToCart(coupon, c, userId)) {
                    BigDecimal applicableSubtotal = computeApplicableSubtotal(coupon, c);
                    discount = computeDiscountAmount(coupon, applicableSubtotal);
                } else {
                    c.setCouponCode(null);
                    carts.save(c);
                    applied = null;
                }
            }
        }
        if (!computeCoupon) {
            discount = BigDecimal.ZERO;
            applied = null;
        }
        BigDecimal total = subtotal.subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;
        boolean changed = (c.getSubtotal() == null || c.getSubtotal().compareTo(subtotal) != 0)
                || (c.getDiscountAmount() == null || c.getDiscountAmount().compareTo(discount) != 0)
                || (c.getTotal() == null || c.getTotal().compareTo(total) != 0);
        if (changed) {
            c.setSubtotal(subtotal);
            c.setDiscountAmount(discount);
            c.setTotal(total);
            c.setUpdatedAt(LocalDateTime.now());
            carts.save(c);
        }
        BigDecimal totalResp = computeCoupon ? total : null;
        return new CartDtos.CartView(c.getId(), c.getUser() == null ? null : c.getUser().getId(), views, totalQty, subtotal, applied, discount, totalResp, c.getUpdatedAt());
    }

    private boolean isCouponApplicableToCart(Coupon cpn, Cart cart, String userId) {
        if (!cpn.isActive()) return false;
        if (isExpired(cpn)) return false;
        List<CouponAssignment> list = assignRepo.findByCouponId(cpn.getId());
        if (list.isEmpty()) return true;
        Set<String> productIds = new HashSet<>();
        Set<String> categoryIds = new HashSet<>();
        for (CartItem ci : items.findByCart_Id(cart.getId())) {
            productIds.add(ci.getProduct().getId());
            if (ci.getProduct().getCategory() != null) categoryIds.add(ci.getProduct().getCategory().getId());
        }
        for (CouponAssignment a : list) {
            switch (a.getAssignedType()) {
                case CUSTOMER -> {
                    if ("*".equals(a.getAssignedId()) && userId != null) return true;
                    if (userId != null && a.getAssignedId().equals(userId)) return true;
                }
                case PRODUCT -> {
                    if ("*".equals(a.getAssignedId())) return true;
                    if (productIds.contains(a.getAssignedId())) return true;
                }
                case CATEGORY -> {
                    if ("*".equals(a.getAssignedId())) return true;
                    if (categoryIds.contains(a.getAssignedId())) return true;
                }
            }
        }
        return false;
    }

    private boolean isExpired(Coupon c) {
        boolean timeExpired = c.getExpiryDate() != null && LocalDateTime.now().isAfter(c.getExpiryDate());
        boolean usesExceeded = c.getMaxUses() != null && c.getUsedCount() != null && c.getUsedCount() >= c.getMaxUses();
        return timeExpired || usesExceeded;
    }

    private BigDecimal computeApplicableSubtotal(Coupon cpn, Cart cart) {
        List<CouponAssignment> list = assignRepo.findByCouponId(cpn.getId());
        BigDecimal subtotal = BigDecimal.ZERO;
        if (list.isEmpty()) {
            for (CartItem ci : items.findByCart_Id(cart.getId())) {
                if (ci == null || ci.getProduct() == null) continue;
                Product p = ci.getProduct();
                BigDecimal price = safeEffectivePrice(p);
                subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(ci.getQuantity() == null ? 0 : ci.getQuantity())));
            }
            return subtotal;
        }
        Set<String> allowedProducts = new HashSet<>();
        Set<String> allowedCategories = new HashSet<>();
        boolean allProducts = false, allCategories = false;
        for (CouponAssignment a : list) {
            if (a.getAssignedType() == CouponAssignment.AssignmentType.PRODUCT) {
                if ("*".equals(a.getAssignedId())) allProducts = true; else allowedProducts.add(a.getAssignedId());
            }
            if (a.getAssignedType() == CouponAssignment.AssignmentType.CATEGORY) {
                if ("*".equals(a.getAssignedId())) allCategories = true; else allowedCategories.add(a.getAssignedId());
            }
        }
        for (CartItem ci : items.findByCart_Id(cart.getId())) {
            if (ci == null || ci.getProduct() == null) continue;
            Product p = ci.getProduct();
            boolean include = false;
            if (allProducts || allowedProducts.contains(p.getId())) include = true;
            if (!include && (allCategories || (p.getCategory() != null && allowedCategories.contains(p.getCategory().getId())))) include = true;
            if (include) {
                BigDecimal price = safeEffectivePrice(p);
                subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(ci.getQuantity() == null ? 0 : ci.getQuantity())));
            }
        }
        return subtotal;
    }

    private BigDecimal computeDiscountAmount(Coupon cpn, BigDecimal applicableSubtotal) {
        if (applicableSubtotal == null || applicableSubtotal.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        if (cpn.getDiscountType() == Coupon.DiscountType.PERCENT) {
            BigDecimal base = cpn.getDiscountValue() == null ? BigDecimal.ZERO : cpn.getDiscountValue();
            BigDecimal pct = base.divide(BigDecimal.valueOf(100), java.math.RoundingMode.HALF_UP);
            BigDecimal amt = applicableSubtotal.multiply(pct);
            if (amt.compareTo(applicableSubtotal) > 0) amt = applicableSubtotal;
            return amt.setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            BigDecimal amt = cpn.getDiscountValue() == null ? BigDecimal.ZERO : cpn.getDiscountValue();
            if (amt.compareTo(applicableSubtotal) > 0) amt = applicableSubtotal;
            return amt.setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    private void initCartDefaults(Cart c) {
        if (c.getSubtotal() == null) c.setSubtotal(java.math.BigDecimal.ZERO);
        if (c.getDiscountAmount() == null) c.setDiscountAmount(java.math.BigDecimal.ZERO);
        if (c.getTotal() == null) c.setTotal(java.math.BigDecimal.ZERO);
    }

    private String normalizeId(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String s) return s;
        if (raw instanceof Number n) return n.toString();
        if (raw instanceof java.util.Map<?,?> m) {
            Object v = m.get("id");
            if (v == null) v = m.get("value");
            if (v == null) v = m.get("key");
            return v != null ? v.toString() : null;
        }
        return raw.toString();
    }

    private BigDecimal safeEffectivePrice(Product p) {
        BigDecimal dp = p.getDiscountedPrice();
        BigDecimal pr = p.getPrice();
        BigDecimal price = (dp != null ? dp : (pr != null ? pr : BigDecimal.ZERO));
        return price;
    }
}
