# Day 2 — Admin Profile

Implemented admin user management endpoints (requires ADMIN role).

Endpoints
- GET /api/admin/users — list (filter by role, search)
- GET /api/admin/users/{id} — details
- POST /api/admin/users — create admin/customer
- PATCH /api/admin/users/{id} — update fields, reset password
- POST /api/admin/users/{id}/ban | /unban — toggle banned
- POST /api/admin/users/{id}/promote | /demote — change role

Details and payloads: see docs/api/admin-users.md

