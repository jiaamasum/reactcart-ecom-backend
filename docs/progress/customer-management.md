# Day 4 — Customer Management

Implemented customer management via admin users API (role = CUSTOMER).

Admin
- GET /api/admin/users?role=CUSTOMER — list customers (search supported)
- GET /api/admin/users/{id} — customer details
- PATCH /api/admin/users/{id} — update customer profile, reset password
- POST /api/admin/users/{id}/ban | /unban — toggle banned

Reference: docs/api/admin-users.md

