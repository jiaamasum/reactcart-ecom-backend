# Day 5 — Cart (Guest + Auth)

Implemented full cart flows for guests and logged-in users, including merge/claim on login and coupon application with server-side totals.

Guest
- POST /api/carts — bootstrap cart id
- POST /api/carts/{cartId}/items — add item
- PATCH /api/carts/{cartId}/items/{productId} — update qty (0 deletes)
- DELETE /api/carts/{cartId}/items/{productId} — remove item
- DELETE /api/carts/{cartId} — clear
- GET /api/carts/{cartId} — cart page (totals)
- PATCH /api/carts/{cartId}/summary — sync summary/coupon
- POST /api/carts/{cartId}/apply-coupon | DELETE /api/carts/{cartId}/coupon

Authenticated
- GET /api/me/cart — my cart
- POST /api/me/cart/merge — claim/merge guest cart on login
- PATCH /api/me/cart/summary — sync summary/coupon
- POST /api/me/cart/apply-coupon | DELETE /api/me/cart/coupon

Details and payloads: see docs/api/cart.md, docs/api/cart-summary-sync.md, docs/api/cart-integration-guide.md

