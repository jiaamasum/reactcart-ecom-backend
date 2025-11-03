package org.masumjia.reactcartecom.coupons;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.masumjia.reactcartecom.common.ApiError;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.masumjia.reactcartecom.coupons.Coupon.DiscountType;
import org.masumjia.reactcartecom.coupons.CouponAssignment.AssignmentType;
import org.masumjia.reactcartecom.coupons.dto.CouponDtos;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin/coupons")
@Tag(name = "Coupons (Admin)")
public class CouponAdminController {
    private final CouponRepository coupons;
    private final CouponAssignmentRepository assignments;
    private final org.masumjia.reactcartecom.catalog.ProductRepository productRepository;
    private final org.masumjia.reactcartecom.catalog.CategoryRepository categoryRepository;
    private final org.masumjia.reactcartecom.user.UserRepository userRepository;

    public CouponAdminController(CouponRepository coupons, CouponAssignmentRepository assignments,
                                 org.masumjia.reactcartecom.catalog.ProductRepository productRepository,
                                 org.masumjia.reactcartecom.catalog.CategoryRepository categoryRepository,
                                 org.masumjia.reactcartecom.user.UserRepository userRepository) {
        this.coupons = coupons;
        this.assignments = assignments;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/summary")
    @Operation(summary = "Coupon counts (total/active/expired)", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CouponDtos.SummaryCounts>> summary() {
        List<Coupon> all = coupons.findAll();
        long total = all.size();
        long expired = all.stream().filter(this::isExpired).count();
        long active = all.stream().filter(c -> c.isActive() && !isExpired(c)).count();
        return ResponseEntity.ok(ApiResponse.success(new CouponDtos.SummaryCounts(total, active, expired)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "List coupons", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<List<CouponDtos.CouponView>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> sort,
            @RequestParam(required = false) Integer limit
    ) {
        Specification<Coupon> spec = Specification.where(null);
        if (search != null && !search.isBlank()) {
            String s = search.toLowerCase();
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("code")), "%" + s + "%"));
        }
        Sort srt = parseSort(sort, Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<Coupon> list = coupons.findAll(spec, srt);
        if (limit != null && limit > 0 && limit < list.size()) {
            list = list.subList(0, limit);
        }
        List<CouponDtos.CouponView> data = list.stream().map(this::toView).toList();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    @Operation(summary = "Get coupon details", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CouponDtos.CouponView>> get(@PathVariable String id) {
        return coupons.findById(id)
                .map(c -> ResponseEntity.ok(ApiResponse.success(toView(c))))
                .orElse(ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Coupon not found"))));
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
    @PostMapping
    @Operation(summary = "Create coupon", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CouponDtos.CouponView>> create(@Valid @RequestBody CouponDtos.CreateCouponRequest req) {
        String code = req.code().trim();
        if (coupons.existsByCode(code)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Code already exists")));
        }
        DiscountType type = parseType(req.discountType());
        if (!validateDiscount(type, req.discount())) {
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", "Invalid discount value for type")));
        }
        Boolean global = req.global();
        List<String> prodIds = normalizeIds(req.productIds());
        List<String> catIds = normalizeIds(req.categoryIds());
        List<String> custIds = normalizeIds(req.customerIds());
        String invalid = validateAssignments(prodIds, catIds, custIds, global);
        if (invalid != null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("BAD_REQUEST", invalid)));
        }

        Coupon c = new Coupon();
        c.setId(nextCouponId());
        c.setCode(code);
        c.setDiscountType(type);
        c.setDiscountValue(req.discount());
        c.setExpiryDate(parseExpiry(req.expiryDate()));
        c.setMaxUses(req.maxUses());
        c.setActive(req.active() == null ? true : req.active());
        c.setUsedCount(0);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        coupons.save(c);
        if (Boolean.TRUE.equals(global)) {
            assignments.deleteByCouponId(c.getId());
        } else {
            replaceAssignments(c, prodIds, catIds, custIds);
        }
        return ResponseEntity.status(201).body(ApiResponse.success(toView(c), Map.of("message", "Coupon created")));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    @Operation(summary = "Update coupon (partial)", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CouponDtos.CouponView>> update(@PathVariable String id, @Valid @RequestBody CouponDtos.UpdateCouponRequest req) {
        return coupons.findById(id).map(c -> {
            if (req.code() != null) {
                String code = req.code().trim();
                if (!code.equals(c.getCode()) && coupons.existsByCode(code)) {
                    return ResponseEntity.badRequest().body(ApiResponse.<CouponDtos.CouponView>error(new ApiError("BAD_REQUEST", "Code already exists")));
                }
                c.setCode(code);
            }
            if (req.discountType() != null) {
                c.setDiscountType(parseType(req.discountType()));
            }
            if (req.discount() != null) {
                if (!validateDiscount(c.getDiscountType(), req.discount())) {
                    return ResponseEntity.badRequest().body(ApiResponse.<CouponDtos.CouponView>error(new ApiError("BAD_REQUEST", "Invalid discount value for type")));
                }
                c.setDiscountValue(req.discount());
            }
            if (req.expiryDate() != null) c.setExpiryDate(parseExpiry(req.expiryDate()));
            if (req.maxUses() != null) c.setMaxUses(req.maxUses());
            if (req.active() != null) c.setActive(req.active());
            c.setUpdatedAt(LocalDateTime.now());
            coupons.save(c);

            if (req.global() != null || req.productIds() != null || req.categoryIds() != null || req.customerIds() != null) {
                Boolean global = req.global();
                List<String> prodIds = normalizeIds(req.productIds());
                List<String> catIds = normalizeIds(req.categoryIds());
                List<String> custIds = normalizeIds(req.customerIds());
                String invalid2 = validateAssignments(prodIds, catIds, custIds, global);
                if (invalid2 != null) {
                    return ResponseEntity.badRequest().body(ApiResponse.<CouponDtos.CouponView>error(new ApiError("BAD_REQUEST", invalid2)));
                }
                if (Boolean.TRUE.equals(global)) {
                    assignments.deleteByCouponId(c.getId());
                } else {
                    replaceAssignments(c, prodIds, catIds, custIds);
                }
            }
            return ResponseEntity.ok(ApiResponse.success(toView(c), Map.of("message", "Coupon updated")));
        }).orElse(ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Coupon not found"))));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete coupon", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable String id) {
        return coupons.findById(id).map(c -> {
            coupons.delete(c);
            return ResponseEntity.ok(ApiResponse.success(null, Map.of("message", "Coupon deleted")));
        }).orElse(ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Coupon not found"))));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate coupon", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CouponDtos.CouponView>> activate(@PathVariable String id) {
        return coupons.findById(id).map(c -> {
            c.setActive(true);
            c.setUpdatedAt(LocalDateTime.now());
            coupons.save(c);
            return ResponseEntity.ok(ApiResponse.success(toView(c), Map.of("message", "Coupon activated")));
        }).orElse(ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Coupon not found"))));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate coupon", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<CouponDtos.CouponView>> deactivate(@PathVariable String id) {
        return coupons.findById(id).map(c -> {
            c.setActive(false);
            c.setUpdatedAt(LocalDateTime.now());
            coupons.save(c);
            return ResponseEntity.ok(ApiResponse.success(toView(c), Map.of("message", "Coupon deactivated")));
        }).orElse(ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "Coupon not found"))));
    }

    private boolean validateDiscount(DiscountType type, BigDecimal value) {
        if (value == null) return false;
        if (type == DiscountType.PERCENT) {
            return value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0;
        }
        return value.compareTo(BigDecimal.ZERO) > 0; // FIXED
    }

    private boolean isExpired(Coupon c) {
        boolean timeExpired = c.getExpiryDate() != null && LocalDateTime.now().isAfter(c.getExpiryDate());
        boolean usesExceeded = c.getMaxUses() != null && c.getUsedCount() != null && c.getUsedCount() >= c.getMaxUses();
        return timeExpired || usesExceeded;
    }

    private DiscountType parseType(String s) {
        if (s == null) throw new IllegalArgumentException("discountType is required");
        return DiscountType.valueOf(s.trim().toUpperCase());
    }

    private CouponDtos.CouponView toView(Coupon c) {
        List<CouponAssignment> list = assignments.findByCouponId(c.getId());
        List<String> p = new ArrayList<>();
        List<String> cat = new ArrayList<>();
        List<String> cust = new ArrayList<>();
        for (CouponAssignment a : list) {
            if (a.getAssignedType() == AssignmentType.PRODUCT) p.add(a.getAssignedId());
            else if (a.getAssignedType() == AssignmentType.CATEGORY) cat.add(a.getAssignedId());
            else if (a.getAssignedType() == AssignmentType.CUSTOMER) cust.add(a.getAssignedId());
        }
        return new CouponDtos.CouponView(
                c.getId(), c.getCode(), c.getDiscountType().name(), c.getDiscountValue(), c.getExpiryDate(), c.getMaxUses(), c.getUsedCount(), c.isActive() && !isExpired(c),
                p, cat, cust, c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    private void replaceAssignments(Coupon c, List<String> productIds, List<String> categoryIds, List<String> customerIds) {
        assignments.deleteByCouponId(c.getId());
        if (productIds != null) {
            for (String id : productIds) {
                CouponAssignment a = new CouponAssignment();
                a.setCoupon(c);
                a.setAssignedType(AssignmentType.PRODUCT);
                a.setAssignedId(id);
                assignments.save(a);
            }
        }
        if (categoryIds != null) {
            for (String id : categoryIds) {
                CouponAssignment a = new CouponAssignment();
                a.setCoupon(c);
                a.setAssignedType(AssignmentType.CATEGORY);
                a.setAssignedId(id);
                assignments.save(a);
            }
        }
        if (customerIds != null) {
            for (String id : customerIds) {
                CouponAssignment a = new CouponAssignment();
                a.setCoupon(c);
                a.setAssignedType(AssignmentType.CUSTOMER);
                a.setAssignedId(id);
                assignments.save(a);
            }
        }
    }

    private String validateAssignments(List<String> productIds, List<String> categoryIds, List<String> customerIds, Boolean global) {
        if (Boolean.TRUE.equals(global)) {
            if ((productIds != null && !productIds.isEmpty()) || (categoryIds != null && !categoryIds.isEmpty()) || (customerIds != null && !customerIds.isEmpty())) {
                return "Do not provide assignment IDs when global=true";
            }
            return null;
        }
        int typesProvided = 0;
        if (productIds != null && !productIds.isEmpty()) typesProvided++;
        if (categoryIds != null && !categoryIds.isEmpty()) typesProvided++;
        if (customerIds != null && !customerIds.isEmpty()) typesProvided++;
        if (typesProvided > 1) {
            return "Provide only one assignment type: products OR categories OR customers";
        }
        // If none provided and global is not explicitly true, treat as GLOBAL (all products)

        if (productIds != null && !productIds.isEmpty()) {
            if (!(productIds.size() == 1 && "*".equals(productIds.get(0)))) {
                int count = productRepository.findAllById(productIds).size();
                if (count != productIds.size()) return "One or more product IDs are invalid";
            }
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            if (!(categoryIds.size() == 1 && "*".equals(categoryIds.get(0)))) {
                int count = categoryRepository.findAllById(categoryIds).size();
                if (count != categoryIds.size()) return "One or more category IDs are invalid";
            }
        }
        if (customerIds != null && !customerIds.isEmpty()) {
            if (customerIds.size() == 1 && "*".equals(customerIds.get(0))) {
                return null; // any customer
            }
            // fetch and ensure role CUSTOMER
            List<org.masumjia.reactcartecom.user.User> users = userRepository.findAllById(customerIds);
            if (users.size() != customerIds.size()) return "One or more customer IDs are invalid";
            boolean allCustomer = users.stream().allMatch(u -> "CUSTOMER".equalsIgnoreCase(u.getRole()));
            if (!allCustomer) return "All assigned users must be CUSTOMER role";
        }
        return null;
    }

    private List<String> normalizeIds(List<?> raw) {
        if (raw == null) return null;
        List<String> out = new ArrayList<>();
        for (Object o : raw) {
            if (o == null) continue;
            if (o instanceof String s) {
                if (!s.isBlank()) out.add(s.trim());
            } else if (o instanceof Number n) {
                out.add(n.toString());
            } else if (o instanceof java.util.Map<?,?> m) {
                Object v = m.get("id");
                if (v == null) v = m.get("value");
                if (v == null) v = m.get("key");
                if (v != null) out.add(v.toString());
            } else {
                // Fallback
                out.add(o.toString());
            }
        }
        return out;
    }

    private LocalDateTime parseExpiry(Object raw) {
        if (raw == null) return null;
        try {
            if (raw instanceof java.time.LocalDateTime ldt) return ldt;
            if (raw instanceof String s) {
                String str = s.trim();
                if (str.isEmpty()) return null;
                // Try common patterns: ISO_OFFSET_DATE_TIME, ISO_LOCAL_DATE_TIME, ISO_INSTANT, ISO_LOCAL_DATE
                try { return java.time.OffsetDateTime.parse(str).toLocalDateTime(); } catch (Exception ignored) {}
                try { return java.time.LocalDateTime.parse(str); } catch (Exception ignored) {}
                try { return java.time.Instant.parse(str).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(); } catch (Exception ignored) {}
                try { return java.time.LocalDate.parse(str).atTime(23,59,59); } catch (Exception ignored) {}
                // Fallback: null if unparsable
                return null;
            }
            if (raw instanceof Number n) {
                long epochMillis = n.longValue();
                return java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String nextCouponId() {
        int max = 0;
        for (String id : coupons.findAllIds()) {
            if (id != null && id.startsWith("cpn-")) {
                try {
                    int n = Integer.parseInt(id.substring(4));
                    if (n > max) max = n;
                } catch (NumberFormatException ignored) {}
            }
        }
        return "cpn-" + (max + 1);
    }
}
