package org.masumjia.reactcartecom.coupons.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CouponDtos {
    public static record CreateCouponRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank String discountType, // PERCENT or FIXED
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal discount,
            Object expiryDate, // accepts multiple formats; null = no expiry
            @Min(0) Integer maxUses, // null = unlimited
            Boolean active,
            Boolean global,
            List<Object> productIds,
            List<Object> categoryIds,
            List<Object> customerIds
    ) {}

    public static record UpdateCouponRequest(
            @Size(max = 100) String code,
            String discountType,
            @DecimalMin(value = "0.0", inclusive = false) BigDecimal discount,
            Object expiryDate,
            @Min(0) Integer maxUses,
            Boolean active,
            Boolean global,
            List<Object> productIds,
            List<Object> categoryIds,
            List<Object> customerIds
    ) {}

    public static record CouponView(
            String id,
            String code,
            String discountType,
            java.math.BigDecimal discount,
            LocalDateTime expiryDate,
            Integer maxUses,
            Integer usedCount,
            boolean active,
            List<String> productIds,
            List<String> categoryIds,
            List<String> customerIds,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public static record SummaryCounts(long total, long active, long expired) {}
}
