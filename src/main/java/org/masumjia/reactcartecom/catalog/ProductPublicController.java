package org.masumjia.reactcartecom.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.masumjia.reactcartecom.catalog.dto.ProductDtos;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products (Public)")
public class ProductPublicController {
    private final ProductRepository products;
    private final ProductImageRepository images;

    public ProductPublicController(ProductRepository products, ProductImageRepository images) {
        this.products = products;
        this.images = images;
    }

    @GetMapping
    @Operation(summary = "List products")
    public ResponseEntity<ApiResponse<List<ProductDtos.ProductSummary>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Boolean inStockOnly
    ) {
        boolean filtered = (search != null && !search.isBlank()) || (categoryId != null && !categoryId.isBlank()) || Boolean.TRUE.equals(inStockOnly);
        List<Product> list;
        if (!filtered) {
            list = products.findAll();
        } else {
            Specification<Product> spec = Specification.where(null);
            if (search != null && !search.isBlank()) {
                spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%"));
            }
            if (categoryId != null && !categoryId.isBlank()) {
                spec = spec.and((root, q, cb) -> cb.equal(root.get("category").get("id"), categoryId));
            }
            if (Boolean.TRUE.equals(inStockOnly)) {
                spec = spec.and((root, q, cb) -> cb.greaterThan(root.get("stock"), 0));
            }
            list = products.findAll(spec);
        }
        List<ProductDtos.ProductSummary> items = list.stream().map(this::toSummary).toList();
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product details by id")
    public ResponseEntity<ApiResponse<ProductDtos.ProductDetail>> getOne(@PathVariable String id) {
        return products.findById(id)
                .map(prod -> ResponseEntity.ok(ApiResponse.success(toDetail(prod))))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.<ProductDtos.ProductDetail>error(new org.masumjia.reactcartecom.common.ApiError("NOT_FOUND", "Product not found"))));
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

    private ProductDtos.ProductDetail toDetail(Product p) {
        List<String> imgs = images.findByProductIdOrderByPositionAsc(p.getId()).stream().map(ProductImage::getUrl).toList();
        String categoryName = p.getCategory() != null ? p.getCategory().getName() : null;
        return new ProductDtos.ProductDetail(
                p.getId(), p.getName(), p.getDescription(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                categoryName,
                p.getPrice(), p.getDiscountedPrice(), p.getDiscount(), p.getStock(), p.getPrimaryImageUrl(), imgs
        );
    }
}
