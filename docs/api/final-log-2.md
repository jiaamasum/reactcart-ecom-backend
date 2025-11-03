what have you removed also document this


• Nothing was removed. I only added and updated code and docs. Below is a concise changelog and a note on potential cleanups you may consider later (kept intact to avoid breakage).

Removals

- None. I did not delete any files, classes, or methods.

What I Added/Changed

- All‑in‑one reference
    - docs/api/api-reference.md:1 — single doc covering all public, user, and admin APIs.
- Orders (auth) now capture checkout contact fields
    - src/main/java/org/masumjia/reactcartecom/orders/dto/OrderDtos.java:11 — CreateAuthedOrderRequest includes name, email, phone.
    - src/main/java/org/masumjia/reactcartecom/orders/OrderController.java:114 — persists those into guestName, guestEmail, guestPhone.
- Admin Orders list (filterable/pageable) + compile fixes
    - src/main/java/org/masumjia/reactcartecom/orders/OrderAdminController.java:16 — list with status/search/minTotal/maxTotal/page/size/sort (fixed Criteria API usage).
    - src/main/java/org/masumjia/reactcartecom/orders/OrderRepository.java:1 — now extends JpaSpecificationExecutor and has helper queries.
- Admin Dashboard API
    - src/main/java/org/masumjia/reactcartecom/admin/AdminDashboardController.java:1 — dashboard KPIs, status distribution, revenue trend, quick stats, recent orders (now includes userId, guestName, guestEmail, guestPhone).
    - src/main/java/org/masumjia/reactcartecom/coupons/CouponRepository.java:1 — countActiveAndNotExpired() to power active coupons.
    - src/main/java/org/masumjia/reactcartecom/catalog/ProductRepository.java:1 — countByStockLessThanEqual() for low‑stock.
- Swagger authorization icons on admin endpoints
    - src/main/java/org/masumjia/reactcartecom/orders/OrderAdminController.java:7 — @SecurityRequirement(name = "bearerAuth").
    - src/main/java/org/masumjia/reactcartecom/admin/AdminDashboardController.java:8 — same.
- Documentation (added/updated)
    - docs/api/orders.md:77 — auth order request includes checkout contact fields.
    - docs/api/admin-orders.md:1 — list filters/pagination detailed.
    - docs/api/admin-dashboard.md:1 — fields mapped to UI widgets.

Potential Cleanups (Kept Intact)

- src/main/java/org/masumjia/reactcartecom/admin/AdminDashboardController.java:1 — injected OrderItemRepository is not used in this controller. Safe to remove injection if you don’t plan to expand recent‑items detail here.
- src/main/java/org/masumjia/reactcartecom/coupons/CouponRepository.java:1 — countByActiveTrue() may be unused now; current dashboard uses countActiveAndNotExpired(). Keep if used elsewhere; otherwise good candidate to
  prune later.

If you want, I can prepare a follow‑up PR to:

- Remove the unused injection/methods after verifying references with a project‑wide usage scan.
- Add a “customerName/customerEmail/isGuest” trio to OrderView so frontend never needs to branch on userId vs guest fields.