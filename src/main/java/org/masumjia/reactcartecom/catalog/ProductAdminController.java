package org.masumjia.reactcartecom.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.masumjia.reactcartecom.catalog.dto.ProductDtos;
import org.masumjia.reactcartecom.common.ApiError;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin/products")
@Tag(name = "Products (Admin)")
public class ProductAdminController {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;

    public ProductAdminController(ProductRepository products, CategoryRepository categories, ProductImageRepository images) {
        this.products = products;
        this.categories = categories;
        this.images = images;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "List all products (admin)", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<List<ProductDtos.ProductSummary>>> list() {
        List<ProductDtos.ProductSummary> items = products.findAll().stream().map(this::toSummary).toList();
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/search")
    @Operation(summary = "Search inventory", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<List<ProductDtos.ProductSummary>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Boolean inStockOnly,
            @RequestParam(required = false) List<String> sort,
            @RequestParam(required = false) Integer limit
    ) {
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
        Sort srt = parseSort(sort, Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<Product> list = products.findAll(spec, srt);
        if (limit != null && limit > 0 && limit < list.size()) {
            list = list.subList(0, limit);
        }
        List<ProductDtos.ProductSummary> data = list.stream().map(this::toSummary).toList();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @Operation(summary = "Create product", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<ProductDtos.ProductDetail>> create(@Valid @RequestBody ProductDtos.CreateProductRequest req) {
        Category cat = categories.findById(req.categoryId()).orElse(null);
        if (cat == null) {
            return ResponseEntity.badRequest().body(ApiResponse.<ProductDtos.ProductDetail>error(new ApiError("BAD_REQUEST", "Category not found")));
        }
        // Treat discountedPrice of 0 as null
        java.math.BigDecimal dp = req.discountedPrice();
        if (dp != null && dp.compareTo(java.math.BigDecimal.ZERO) == 0) {
            dp = null;
        }
        if (dp != null && dp.compareTo(req.price()) >= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.<ProductDtos.ProductDetail>error(new ApiError("BAD_REQUEST", "Discounted price must be less than price")));
        }
        Product p = new Product();
        p.setId(nextProductId());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setCategory(cat);
        p.setPrice(req.price());
        p.setDiscountedPrice(dp);
        Integer d = calcDiscount(req.price(), dp);
        p.setDiscount(d == null ? 0 : d);
        p.setStock(req.stock());
        p.setPrimaryImageUrl(req.primaryImageUrl());
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        products.save(p);
        if (req.images() != null && !req.images().isEmpty()) {
            int pos = 0;
            for (String url : req.images()) {
                ProductImage img = new ProductImage();
                img.setId(UUID.randomUUID().toString());
                img.setProduct(p);
                img.setUrl(url);
                img.setPosition(pos++);
                images.save(img);
            }
        }
        return ResponseEntity.status(201).body(ApiResponse.success(toDetail(p), Map.of("message", "Product created")));
    }

    private Sort parseSort(List<String> sortParams, Sort defaultSort) {
        if (sortParams == null || sortParams.isEmpty()) return defaultSort;
        Sort result = Sort.unsorted();
        for (String p : sortParams) {
            if (p == null || p.isBlank()) continue;
            String[] parts = p.split(",");
            String field = parts[0].trim();
            Sort.Direction dir = (parts.length > 1 && parts[1] != null && parts[1].trim().equalsIgnoreCase("desc"))
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            result = result.and(Sort.by(dir, field));
        }
        return result.isUnsorted() ? defaultSort : result;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    @Operation(summary = "Replace product", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<ProductDtos.ProductDetail>> put(@PathVariable String id, @Valid @RequestBody ProductDtos.CreateProductRequest req) {
        return updateInternal(id, new ProductDtos.UpdateProductRequest(req.name(), req.description(), req.categoryId(), req.price(), req.discountedPrice(), req.stock(), req.primaryImageUrl(), req.images()) , true);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    @Operation(summary = "Update product", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<ProductDtos.ProductDetail>> patch(@PathVariable String id, @Valid @RequestBody ProductDtos.UpdateProductRequest req) {
        return updateInternal(id, req, false);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/stock")
    @Operation(summary = "Update product stock", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<ProductDtos.ProductDetail>> updateStock(@PathVariable String id, @Valid @RequestBody ProductDtos.UpdateStockRequest req) {
        Product p = products.findById(id).orElse(null);
        if (p == null) {
            return ResponseEntity.status(404).body(ApiResponse.<ProductDtos.ProductDetail>error(new ApiError("NOT_FOUND", "Product not found")));
        }
        p.setStock(req.stock());
        p.setUpdatedAt(LocalDateTime.now());
        products.save(p);
        return ResponseEntity.ok(ApiResponse.success(toDetail(p), Map.of("message", "Stock updated")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable String id) {
        return products.findById(id).map(p -> {
            products.delete(p); // Cascade + orphanRemoval on images handles children
            return ResponseEntity.ok(ApiResponse.success(null, Map.of("message", "Product deleted")));
        }).orElse(ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Product not found"))));
    }

    private ResponseEntity<ApiResponse<ProductDtos.ProductDetail>> updateInternal(String id, ProductDtos.UpdateProductRequest req, boolean replace) {
        Product p = products.findById(id).orElse(null);
        if (p == null) {
            return ResponseEntity.status(404).body(ApiResponse.<ProductDtos.ProductDetail>error(new ApiError("NOT_FOUND", "Product not found")));
        }
        if (req.categoryId() != null) {
            Category cat = categories.findById(req.categoryId()).orElse(null);
            if (cat == null) {
                return ResponseEntity.badRequest().body(ApiResponse.<ProductDtos.ProductDetail>error(new ApiError("BAD_REQUEST", "Category not found")));
            }
            p.setCategory(cat);
        }
        if (req.name() != null) p.setName(req.name());
        if (req.description() != null) p.setDescription(req.description());
        if (req.price() != null) p.setPrice(req.price());
        // Update discountedPrice: 0 or missing means null
        if (req.discountedPrice() != null) {
            java.math.BigDecimal dp = req.discountedPrice();
            if (dp.compareTo(java.math.BigDecimal.ZERO) == 0) p.setDiscountedPrice(null); else p.setDiscountedPrice(dp);
        } else {
            // Field omitted: clear it
            p.setDiscountedPrice(null);
        }
        if (req.stock() != null) p.setStock(req.stock());
        if (req.primaryImageUrl() != null || replace) p.setPrimaryImageUrl(req.primaryImageUrl());

        if (p.getDiscountedPrice() != null && p.getPrice() != null && p.getDiscountedPrice().compareTo(p.getPrice()) >= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.<ProductDtos.ProductDetail>error(new ApiError("BAD_REQUEST", "Discounted price must be less than price")));
        }
        Integer dd = calcDiscount(p.getPrice(), p.getDiscountedPrice());
        p.setDiscount(dd == null ? 0 : dd);
        p.setUpdatedAt(LocalDateTime.now());
        products.save(p);

        if (req.images() != null) {
            images.deleteByProductId(p.getId());
            int pos = 0;
            for (String url : req.images()) {
                ProductImage img = new ProductImage();
                img.setId(UUID.randomUUID().toString());
                img.setProduct(p);
                img.setUrl(url);
                img.setPosition(pos++);
                images.save(img);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(toDetail(p), Map.of("message", "Product updated")));
    }

    private static Integer calcDiscount(BigDecimal price, BigDecimal discounted) {
        if (price == null || discounted == null) return null;
        if (discounted.compareTo(price) >= 0) return 0;
        return price.compareTo(BigDecimal.ZERO) == 0 ? 0 : price.subtract(discounted).multiply(BigDecimal.valueOf(100)).divide(price, java.math.RoundingMode.HALF_UP).intValue();
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

    private String nextProductId() {
        int max = 0;
        for (String id : products.findAllIds()) {
            if (id != null && id.startsWith("prod-")) {
                try {
                    int n = Integer.parseInt(id.substring(5));
                    if (n > max) max = n;
                } catch (NumberFormatException ignored) {}
            }
        }
        return "prod-" + (max + 1);
    }
}

