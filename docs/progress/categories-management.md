# Day 3 — Categories Management

Implemented public category product listings by id and slug.

Public
- GET /api/categories/{categoryId}/products — list by category id
- GET /api/categories/slug/{slug}/products — list by slug

Filters
- search: substring on name
- inStockOnly: true to restrict to stock > 0

Details: see docs/api/products-by-category.md

