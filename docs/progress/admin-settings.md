# Day 2 — Admin Settings

Implemented store, SEO, and currency settings APIs (ADMIN only). Public settings also exposed for storefront.

Admin Endpoints
- GET/PUT /api/admin/settings/store — store info
- GET/PUT /api/admin/settings/seo — SEO metadata
- GET/PUT /api/admin/settings/currency — default currency (ISO 4217)

Public Endpoint
- GET /api/settings — consolidated settings for storefront

Details and payloads: see docs/api/settings.md

