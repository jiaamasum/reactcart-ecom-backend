# Day 5 — Coupons (Admin + Public)

Implemented admin coupon CRUD with assignments and public coupon validation/redeem.

Admin
- GET /api/admin/coupons/summary — totals
- GET /api/admin/coupons — list/search/sort
- POST /api/admin/coupons — create (PERCENT | FIXED)
- PATCH /api/admin/coupons/{id} — update
- GET /api/admin/coupons/{id} — details
- DELETE /api/admin/coupons/{id} — delete

Public
- GET /api/coupons/{code}/validate — validate and preview discount
- POST /api/coupons/{code}/redeem — increment usage on success

Details: see docs/api/admin-coupons.md and docs/api/public-coupons.md

