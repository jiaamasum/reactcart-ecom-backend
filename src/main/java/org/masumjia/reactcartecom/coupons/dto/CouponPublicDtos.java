package org.masumjia.reactcartecom.coupons.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public class CouponPublicDtos {
    public static record ValidateRequest(
            String customerId,
            List<String> productIds,
            List<String> categoryIds,
            @DecimalMin(value = "0.0", inclusive = true) BigDecimal subtotal
    ) {}

    public static record ValidateResponse(
            String code,
            boolean valid,
            String reason,
            String discountType,
            java.math.BigDecimal discount,
            java.math.BigDecimal discountAmount,
            String appliedScope, // GLOBAL | CUSTOMER | PRODUCT | CATEGORY | NONE
            java.time.LocalDateTime expiryDate,
            Integer maxUses,
            Integer usedCount
    ) {}

    public static record RedeemRequest(
            String customerId,
            List<String> productIds,
            List<String> categoryIds
    ) {}
}
