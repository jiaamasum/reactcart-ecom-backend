# Day 5 — Orders (Guest + Admin)

Implemented guest and authenticated checkout, order retrieval, user stats, and admin order management.

Guest + Auth
- POST /api/orders — guest checkout from cartId
- POST /api/me/orders — authenticated checkout from user cart
- GET /api/orders/{id} | /api/orders/number/{orderNumber}
- GET /api/me/orders — my orders
- GET /api/me/orders/stats — totals and spend

Admin
- GET /api/admin/orders — list with filters/sorting/pagination
- GET /api/admin/orders/{id} — details
- PATCH /api/admin/orders/{id}/status — status update
- DELETE /api/admin/orders/{id} — delete

Details: see docs/api/orders.md and docs/api/admin-orders.md

