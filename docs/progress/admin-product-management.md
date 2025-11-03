# Day 3 — Admin Product Management

Implemented admin and public product APIs, plus category admin.

Public
- GET /api/products — list with search/category/stock filters
- GET /api/products/{id} — product details

Admin
- GET /api/admin/products — list
- POST /api/admin/products — create
- PUT/PATCH /api/admin/products/{id} — update
- DELETE /api/admin/products/{id} — delete
- Admin categories: GET/POST under /api/admin/categories

Details and payloads: see docs/api/products-and-categories.md

