# Day 1 — Authentication & User Profile

Implemented JWT-based auth and user profile endpoints.

Endpoints
- POST /api/auth/register — create account and return accessToken
- POST /api/auth/login — issue accessToken for valid credentials
- POST /api/auth/logout — stateless logout (client discards token)
- POST /api/auth/reset-password — reset with validation
- GET /api/user-details — current user details (Bearer token)
- PUT/PATCH /api/user-details — update profile fields

Details and payloads: see docs/api/auth-and-user-details.md

