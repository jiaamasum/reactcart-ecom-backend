package org.masumjia.reactcartecom.coupons;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.masumjia.reactcartecom.common.ApiError;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.masumjia.reactcartecom.coupons.CouponAssignment.AssignmentType;
import org.masumjia.reactcartecom.coupons.dto.CouponPublicDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/coupons")
@Tag(name = "Coupons (Public)")
public class CouponPublicController {
    private final CouponRepository coupons;
    private final CouponAssignmentRepository assignments;

    public CouponPublicController(CouponRepository coupons, CouponAssignmentRepository assignments) {
        this.coupons = coupons;
        this.assignments = assignments;
    }

    @GetMapping("/{code}/validate")
    @Operation(summary = "Validate coupon by code")
    public ResponseEntity<ApiResponse<CouponPublicDtos.ValidateResponse>> validate(
            @PathVariable String code,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) List<String> productIds,
            @RequestParam(required = false) List<String> categoryIds,
            @RequestParam(required = false) java.math.BigDecimal subtotal
    ) {
        return coupons.findAll().stream()
                .filter(c -> c.getCode().equalsIgnoreCase(code))
                .findFirst()
                .map(c -> ResponseEntity.ok(ApiResponse.success(toValidateResponse(c, customerId, productIds, categoryIds, subtotal))))
                .orElse(ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Coupon not found"))));
    }

    @PostMapping("/{code}/redeem")
    @Transactional
    @Operation(summary = "Redeem coupon (increments usage)")
    public ResponseEntity<ApiResponse<Object>> redeem(@PathVariable String code, @Valid @RequestBody CouponPublicDtos.RedeemRequest req) {
        Coupon c = coupons.findAll().stream().filter(x -> x.getCode().equalsIgnoreCase(code)).findFirst().orElse(null);
        if (c == null) {
            return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Coupon not found")));
        }
        CouponPublicDtos.ValidateResponse vr = toValidateResponse(c, req.customerId(), req.productIds(), req.categoryIds(), null);
        if (!vr.valid()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Coupon not valid: " + vr.reason())));
        }
        // Enforce usage limit atomically enough for demo; production would use DB-side locking
        Integer max = c.getMaxUses();
        Integer used = c.getUsedCount() == null ? 0 : c.getUsedCount();
        if (max != null && used >= max) {
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Usage limit reached")));
        }
        c.setUsedCount(used + 1);
        c.setUpdatedAt(LocalDateTime.now());
        coupons.save(c);
        Map<String, Object> data = new HashMap<>();
        data.put("code", c.getCode());
        data.put("usedCount", c.getUsedCount());
        return ResponseEntity.ok(ApiResponse.success((Object) data, Map.of("message", "Coupon redeemed")));
    }

    private CouponPublicDtos.ValidateResponse toValidateResponse(Coupon c, String customerId, List<String> productIds, List<String> categoryIds, java.math.BigDecimal subtotal) {
        if (!c.isActive()) {
            return new CouponPublicDtos.ValidateResponse(c.getCode(), false, "Coupon inactive", c.getDiscountType().name(), c.getDiscountValue(), BigDecimal.ZERO, "NONE", c.getExpiryDate(), c.getMaxUses(), c.getUsedCount());
        }
        boolean expired = isExpired(c);
        if (expired) {
            return new CouponPublicDtos.ValidateResponse(c.getCode(), false, "Coupon expired", c.getDiscountType().name(), c.getDiscountValue(), BigDecimal.ZERO, "NONE", c.getExpiryDate(), c.getMaxUses(), c.getUsedCount());
        }
        List<CouponAssignment> list = assignments.findByCouponId(c.getId());
        String appliedScope = "GLOBAL"; // default if no assignments
        boolean hasAssignments = !list.isEmpty();
        boolean ok = true;
        if (hasAssignments) {
            ok = false;
            // customer match
            if (customerId != null) {
                for (CouponAssignment a : list) {
                    if (a.getAssignedType() == AssignmentType.CUSTOMER && ("*".equals(a.getAssignedId()) || a.getAssignedId().equals(customerId))) {
                        ok = true; appliedScope = "CUSTOMER"; break;
                    }
                }
            }
            // product match
            if (!ok && productIds != null && !productIds.isEmpty()) {
                Set<String> pset = new HashSet<>(productIds);
                for (CouponAssignment a : list) {
                    if (a.getAssignedType() == AssignmentType.PRODUCT && ("*".equals(a.getAssignedId()) || pset.contains(a.getAssignedId()))) { ok = true; appliedScope = "PRODUCT"; break; }
                }
            }
            // category match
            if (!ok && categoryIds != null && !categoryIds.isEmpty()) {
                Set<String> cset = new HashSet<>(categoryIds);
                for (CouponAssignment a : list) {
                    if (a.getAssignedType() == AssignmentType.CATEGORY && ("*".equals(a.getAssignedId()) || cset.contains(a.getAssignedId()))) { ok = true; appliedScope = "CATEGORY"; break; }
                }
            }
            // If still not ok, also handle ALL assignments even when no ids were sent
            if (!ok) {
                for (CouponAssignment a : list) {
                    if ("*".equals(a.getAssignedId())) {
                        switch (a.getAssignedType()) {
                            case PRODUCT -> { ok = true; appliedScope = "PRODUCT"; }
                            case CATEGORY -> { ok = true; appliedScope = "CATEGORY"; }
                            case CUSTOMER -> { ok = true; appliedScope = "CUSTOMER"; }
                        }
                        if (ok) break;
                    }
                }
            }
        }
        if (!ok) {
            return new CouponPublicDtos.ValidateResponse(c.getCode(), false, "Coupon not applicable to selection", c.getDiscountType().name(), c.getDiscountValue(), BigDecimal.ZERO, "NONE", c.getExpiryDate(), c.getMaxUses(), c.getUsedCount());
        }
        BigDecimal discountAmt = BigDecimal.ZERO;
        if (subtotal != null) {
            discountAmt = computeDiscountAmount(c.getDiscountType(), c.getDiscountValue(), subtotal);
        }
        return new CouponPublicDtos.ValidateResponse(c.getCode(), true, null, c.getDiscountType().name(), c.getDiscountValue(), discountAmt, appliedScope, c.getExpiryDate(), c.getMaxUses(), c.getUsedCount());
    }

    private static BigDecimal computeDiscountAmount(Coupon.DiscountType type, BigDecimal value, BigDecimal subtotal) {
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        if (type == Coupon.DiscountType.PERCENT) {
            BigDecimal pct = value.divide(BigDecimal.valueOf(100), java.math.RoundingMode.HALF_UP);
            BigDecimal amt = subtotal.multiply(pct);
            // cap at subtotal
            if (amt.compareTo(subtotal) > 0) amt = subtotal;
            return amt.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        // FIXED
        BigDecimal amt = value;
        if (amt.compareTo(subtotal) > 0) amt = subtotal;
        return amt.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private boolean isExpired(Coupon c) {
        boolean timeExpired = c.getExpiryDate() != null && LocalDateTime.now().isAfter(c.getExpiryDate());
        boolean usesExceeded = c.getMaxUses() != null && c.getUsedCount() != null && c.getUsedCount() >= c.getMaxUses();
        return timeExpired || usesExceeded;
    }
}
