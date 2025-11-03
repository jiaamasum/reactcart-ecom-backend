# API Reference (All-in-One)

Base URL: `http://localhost:8080`
Envelope: `{ data, meta, error }` where `error = { code, message, fields? }`.

Auth schemes
- Public: no auth
- User: Bearer token (JWT subject = userId)
- Admin: Bearer token with `ROLE_ADMIN`

Sections
- Public Coupons
- Cart (Guest + Auth) + Coupon flow
- Cart Summary Sync
- Orders (Guest + Auth) + Cancel
- My Order Stats
- Orders (Admin)
- Admin Dashboard

---

## Public Coupons
- Validate by code: GET `/api/coupons/{code}/validate`
  - Query (optional): `customerId`, `productIds`, `categoryIds`, `subtotal`
- Redeem (increment usage): POST `/api/coupons/{code}/redeem`
  - Body: `{ customerId?, productIds?, categoryIds? }`

## Cart – Guest (no auth)
- Create cart: POST `/api/carts` → `{ data: { cartId } }`
- Add item: POST `/api/carts/{cartId}/items` `{ productId, quantity? }`
- Update qty: PATCH `/api/carts/{cartId}/items/{productId}` `{ quantity }`
- Remove item: DELETE `/api/carts/{cartId}/items/{productId}`
- Clear cart: DELETE `/api/carts/{cartId}`
- Get cart (page render): GET `/api/carts/{cartId}` → `CartView`
- Coupons on cart:
  - Apply: POST `/api/carts/{cartId}/apply-coupon` `{ code }`
  - Remove: DELETE `/api/carts/{cartId}/coupon`

## Cart – Auth (Bearer)
- Get my cart (creates if missing): GET `/api/me/cart` → `CartView`
- Merge guest cart: POST `/api/me/cart/merge` `{ guestCartId, strategy: "sum"|"replace" }`
- Coupons on cart:
  - Apply: POST `/api/me/cart/apply-coupon` `{ code }`
  - Remove: DELETE `/api/me/cart/coupon`

`CartView`
- `id`, `userId|null`, `items[]`, `totalQuantity`, `subtotal`, `appliedCouponCode|null`, `discountAmount`, `total|null on write`, `updatedAt`

## Cart Summary Sync (server is source of truth)
- Guest: PATCH `/api/carts/{cartId}/summary`
- Auth: PATCH `/api/me/cart/summary`
- Body (optional):
```
{
  code?: string|null,        // apply/remove coupon
  subtotal?: number,         // optional snapshot; if provided, server stores
  discountAmount?: number,
  total?: number
}
```
- Response: `{ data: CartView, meta.message: "Summary synced" | "Summary synced (client)" }`

Notes
- All item write endpoints recompute and persist totals; responses include computed totals.
- Reads auto-clear invalid coupons and persist the change.

## Orders – Guest (no auth)
- Create order: POST `/api/orders`
```
{
  cartId: string,
  name: string,
  email: string,
  phone?: string,
  address: string,
  city: string,
  postalCode: string,
  paymentMethod: "COD"|"CARD",
  card?: { number: string(16 digits), expiry: "MM/YY" future, cvv: string }
}
```
- Stock decremented atomically; 409 `OUT_OF_STOCK` with `error.fields[productId]=available` on failure.
- On success: cart is cleared (items removed, coupon cleared, totals set to 0).

## Orders – Auth (Bearer)
- Create order: POST `/api/me/orders`
```
{
  name: string,              // captured on order even if logged-in
  email: string,             // captured on order even if logged-in
  phone?: string,
  address: string,
  city: string,
  postalCode: string,
  paymentMethod: "COD"|"CARD",
  card?: { ... }
}
```
- Order also links `userId` from token; order’s display/contact uses submitted name/email/phone.
- Get by id: GET `/api/orders/{id}` (owner-only if user-linked)
- Get by number: GET `/api/orders/number/{orderNumber}`
- List my orders: GET `/api/me/orders`
- Cancel within 12 hours: PATCH `/api/me/orders/{id}/cancel` (POST also accepted)

`OrderView`
- `id`, `orderNumber`, `orderNumberFormatted`, `userId|null`, `status`, `paymentMethod`, `shippingAddress`,
  `guestName`, `guestEmail`, `guestPhone`, `subtotal`, `discount`, `total`, `couponCode`, `createdAt`, `items[]`,
  `coupon: { code, discountAmount }`

## My Order Stats (Bearer)
- GET `/api/me/orders/stats` → `{ totalOrders, completedOrders, totalSpent }`
  - completedOrders + totalSpent count only `DELIVERED` orders

## Orders – Admin (Bearer + ROLE_ADMIN)
- List (filtered): GET `/api/admin/orders`
  - Query: `status`, `search`, `minTotal`, `maxTotal`, `page`, `size`, `sort`
  - Returns: `{ data: OrderView[], meta: { total, page, size, totalPages } }`
- Get details: GET `/api/admin/orders/{id}`
- Update status: PATCH `/api/admin/orders/{id}/status` `{ status }`
- Delete: DELETE `/api/admin/orders/{id}`

## Admin Dashboard (Bearer + ROLE_ADMIN)
- GET `/api/admin/dashboard` (`lowStockThreshold` optional)
- Returns:
```
{
  totalRevenue, totalOrders, totalCustomers, totalProducts,
  statusDistribution: { PENDING, CONFIRMED, IN_PROCESS, DELIVERED, CANCELLED },
  revenueTrend: [ { year, month, label, total } ],
  quickStats: { completedOrders, pendingOrders, activeCoupons, lowStockProducts },
  recentOrders: [ { id, orderNumber, orderNumberFormatted, createdAt, status, total,
                   customer, userId, guestName, guestEmail, guestPhone } ]
}
```

---

## Error Codes (common)
- `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `BAD_REQUEST`
- Cart-specific: `OUT_OF_STOCK`
- Orders cancel: `CANCEL_WINDOW_EXPIRED`

## Notes & Conventions
- Send `Content-Type: application/json` for JSON bodies.
- Coupon code comparisons are case-insensitive.
- Monetary fields are server-owned; trust `subtotal`, `discountAmount/discount`, and `total` returned.
- Swagger: endpoints with the lock icon require `bearerAuth` — click Authorize and paste `Bearer <TOKEN>`.

