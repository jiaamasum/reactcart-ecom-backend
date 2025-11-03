# ReactCartEcom — Spring Boot E‑Commerce API

Production‑ready backend for a React storefront. Provides authentication, user/admin profiles, catalog and product images, categories, cart, coupons, orders, inventory, customers, and admin settings. Clean modular packages, DTOs, and global API error handling with documented endpoints via Swagger UI.

## Key Features
- Authentication with JWT (login, access tokens)
- User profile and Admin profile; Admin settings
- Product and image management; Categories
- Inventory and Customer management
- Cart with items, totals, and validations
- Coupons (admin + public) with assignments
- Orders with statuses and payment methods
- Consistent `ApiResponse` and global exception handler
- OpenAPI Docs via springdoc Swagger UI

## Tech Stack
- Java 17, Spring Boot 3.5.x, Spring MVC, Spring Security, JPA/Hibernate
- MySQL Connector/J
- JJWT for JWT handling
- springdoc-openapi for API docs
- Maven Wrapper

## Getting Started
### Prerequisites
- Java 17+
- MySQL 8+

### Database
1. Create a database named `reactcart_db`.
2. Set credentials in `src/main/resources/application.properties`:
   - `spring.datasource.url`
   - `spring.datasource.username`
   - `spring.datasource.password`

### Environment
- `JWT_SECRET` (optional; defaults provided in `application.properties`)
- `CORS_ORIGINS` (default: `http://localhost:3000`)

### Run
- Windows: `mvnw.cmd spring-boot:run`
- macOS/Linux: `./mvnw spring-boot:run`

### Build & Test
- Build: `./mvnw clean package`
- Test: `./mvnw test`

### API Docs
- Swagger UI: `http://localhost:8080/swagger-ui`

## Package Overview
- `auth` — controllers, DTOs, JWT filter/service
- `user` — user & admin controllers, DTOs, repo
- `catalog` — products, categories, images; admin/public controllers
- `cart` — cart, items, repos, controller
- `coupons` — coupons, assignments; admin/public controllers
- `orders` — orders, items, repos; admin/public controllers
- `settings` — store settings; admin/public controllers
- `common` — API response, error handling
- `config` — OpenAPI, CORS/static config

## Development Timeline
- Day 1 (Frontend): authentication + user profile integration
- Days 2–5 (Backend): admin profile/settings, products, categories, inventory, customers, cart, coupons, orders, dashboard, others

## Branching & Releases
- Work is organized under a `release` branch with feature sub‑branches (e.g., `authentication-userprofile`, `admin-profile`, `admin-settings`, `admin-product-management`, `categories-management`, `inventory-management`, `customer-management`, `cart`, `coupon`, `orders`, `dashboard`, `others`).
- Day‑based commits recommended: `Day X: <what worked>`.
- Merge feature branches into `release` progressively, then merge `release` into `main` for the final cut.
