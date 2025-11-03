package org.masumjia.reactcartecom.catalog.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public class ProductDtos {
    public static record CreateProductRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank String description,
            @NotBlank String categoryId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
            @DecimalMin(value = "0.0", inclusive = true) BigDecimal discountedPrice,
            @NotNull @Min(0) Integer stock,
            @Size(max = 2048) String primaryImageUrl,
            List<@Size(max = 2048) String> images
    ) {}
    
    public static record UpdateStockRequest(
            @NotNull @Min(0) Integer stock
    ) {}

    public static record UpdateProductRequest(
            @Size(max = 200) String name,
            String description,
            String categoryId,
            @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
            @DecimalMin(value = "0.0", inclusive = false) BigDecimal discountedPrice,
            @Min(0) Integer stock,
            @Size(max = 2048) String primaryImageUrl,
            List<@Size(max = 2048) String> images
    ) {}

    public static record ProductSummary(
            String id,
            String name,
            String description,
            String categoryId,
            String categoryName,
            java.math.BigDecimal price,
            java.math.BigDecimal discountedPrice,
            Integer discount,
            Integer stock,
            String primaryImageUrl
    ) {}

    public static record ProductDetail(
            String id,
            String name,
            String description,
            String categoryId,
            String categoryName,
            java.math.BigDecimal price,
            java.math.BigDecimal discountedPrice,
            Integer discount,
            Integer stock,
            String primaryImageUrl,
            List<String> images
    ) {}
}
