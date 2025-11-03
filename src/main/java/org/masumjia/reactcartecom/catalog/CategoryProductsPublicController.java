package org.masumjia.reactcartecom.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.masumjia.reactcartecom.catalog.dto.ProductDtos;
import org.masumjia.reactcartecom.common.ApiError;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Products By Category (Public)")
public class CategoryProductsPublicController {
    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryProductsPublicController(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @GetMapping("/{categoryId}/products")
    @Operation(summary = "List products by category ID")
    public ResponseEntity<ApiResponse<List<ProductDtos.ProductSummary>>> byId(
            @PathVariable String categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean inStockOnly
    ) {
        if (!categories.existsById(categoryId)) {
            return ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Category not found")));
        }
        Specification<Product> spec = Specification.where((root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        if (search != null && !search.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%"));
        }
        if (Boolean.TRUE.equals(inStockOnly)) {
            spec = spec.and((root, q, cb) -> cb.greaterThan(root.get("stock"), 0));
        }
        List<ProductDtos.ProductSummary> list = products.findAll(spec).stream().map(this::toSummary).toList();
        return ResponseEntity.ok(ApiResponse.success(list, Map.of("count", list.size())));
    }

    @GetMapping("/slug/{slug}/products")
    @Operation(summary = "List products by category slug")
    public ResponseEntity<ApiResponse<List<ProductDtos.ProductSummary>>> bySlug(
            @PathVariable String slug,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean inStockOnly
    ) {
        return categories.findBySlug(slug)
                .map(cat -> byId(cat.getId(), search, inStockOnly))
                .orElse(ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Category not found"))));
    }

    private ProductDtos.ProductSummary toSummary(Product p) {
        String categoryName = p.getCategory() != null ? p.getCategory().getName() : null;
        return new ProductDtos.ProductSummary(
                p.getId(), p.getName(), p.getDescription(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                categoryName,
                p.getPrice(), p.getDiscountedPrice(), p.getDiscount(), p.getStock(), p.getPrimaryImageUrl()
        );
    }
}