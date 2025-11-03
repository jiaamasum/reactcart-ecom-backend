package org.masumjia.reactcartecom.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.masumjia.reactcartecom.catalog.dto.CategoryDtos;
import org.masumjia.reactcartecom.common.ApiError;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Categories (Admin)")
public class CategoryController {
    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryController(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @GetMapping("/categories")
    @Operation(summary = "List categories (public)")
    public ResponseEntity<ApiResponse<List<CategoryDtos.CategoryView>>> list() {
        List<CategoryDtos.CategoryView> list = categories.findAll().stream()
                .map(c -> new CategoryDtos.CategoryView(c.getId(), c.getName(), c.getSlug()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/categories")
    @Operation(summary = "List categories (admin)", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> adminList() {
        List<Map<String, Object>> list = categories.findAll().stream().map(c -> {
            long count = products.count((root, q, cb) -> cb.equal(root.get("category").get("id"), c.getId()));
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("slug", c.getSlug());
            m.put("productsCount", count);
            return m;
        }).toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/categories")
    @Operation(summary = "Create category", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CategoryDtos.CategoryView>> create(@Valid @RequestBody CategoryDtos.CreateCategoryRequest req) {
        String slug = uniqueSlug(slugify(req.name()));
        String id = nextCategoryId();
        Category c = new Category();
        c.setId(id);
        c.setName(req.name());
        c.setSlug(slug);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        categories.save(c);
        return ResponseEntity.status(201).body(ApiResponse.success(new CategoryDtos.CategoryView(c.getId(), c.getName(), c.getSlug()), Map.of("message", "Category created")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/categories/{id}")
    @Operation(summary = "Update category", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CategoryDtos.CategoryView>> update(@PathVariable String id, @Valid @RequestBody CategoryDtos.UpdateCategoryRequest req) {
        return categories.findById(id).map(c -> {
            if (req.name() != null) c.setName(req.name());
            if (req.slug() != null) {
                String slug = req.slug().trim();
                if (!slug.equals(c.getSlug()) && categories.existsBySlug(slug)) {
                    return ResponseEntity.badRequest().body(ApiResponse.<CategoryDtos.CategoryView>error(new ApiError("BAD_REQUEST", "Slug already exists")));
                }
                c.setSlug(slug);
            }
            c.setUpdatedAt(LocalDateTime.now());
            categories.save(c);
            return ResponseEntity.ok(ApiResponse.success(new CategoryDtos.CategoryView(c.getId(), c.getName(), c.getSlug()), Map.of("message", "Category updated")));
        }).orElseGet(() -> ResponseEntity.status(404)
                .body(ApiResponse.<CategoryDtos.CategoryView>error(new ApiError("NOT_FOUND", "Category not found"))));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/categories/{id}")
    @Operation(summary = "Delete category", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable String id) {
        return categories.findById(id).map(c -> {
            categories.delete(c);
            return ResponseEntity.ok(ApiResponse.success(null, Map.of("message", "Category deleted")));
        }).orElseGet(() -> ResponseEntity.status(404)
                .body(ApiResponse.<Object>error(new ApiError("NOT_FOUND", "Category not found"))));
    }

    private static String slugify(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
        n = n.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return n;
    }

    private String uniqueSlug(String base) {
        String slug = base;
        int i = 2;
        while (categories.existsBySlug(slug)) {
            slug = base + "-" + i;
            i++;
        }
        return slug;
    }

    private String nextCategoryId() {
        int max = 0;
        for (String id : categories.findAllIds()) {
            if (id != null && id.startsWith("cat-")) {
                try {
                    int n = Integer.parseInt(id.substring(4));
                    if (n > max) max = n;
                } catch (NumberFormatException ignored) {}
            }
        }
        return "cat-" + (max + 1);
    }
}
