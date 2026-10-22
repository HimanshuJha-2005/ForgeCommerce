# ForgeCommerce

Commerce backend and storefront built with Spring Boot, PostgreSQL, and React. It covers authentication, a product catalog, a cart, transactional checkout, order lifecycle, and simulated payments.

[![backend](https://github.com/HimanshuJha-2005/ForgeCommerce/actions/workflows/backend.yml/badge.svg)](https://github.com/HimanshuJha-2005/ForgeCommerce/actions)

## Features

- Registration and login with JWT (one-hour expiry) and BCrypt hashing. Roles `CUSTOMER`, `SELLER`, and `ADMIN` are enforced on the server.
- Product catalog with search, category filter, sorting, and pagination. Sellers manage their products and stock; categories are admin-only.
- Seller summary with product counts, averages, and category breakdown.
- Per-user cart with stock validation on every change.
- Checkout in one transaction: lock stock rows, validate, decrement, create the order and payment, snapshot prices, clear the cart. An idempotency key makes retries return the original order.
- Order lifecycle `PENDING -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED`, plus `CANCELLED`. Illegal transitions are rejected.
- Simulated payment outcome drives confirmation or cancellation and restocks on failure.
- React storefront with browsing, cart, checkout, order history, and a seller dashboard.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.2.5, Spring Security, Spring Data JPA, JJWT |
| Database | PostgreSQL 16, Flyway migrations, H2 for tests |
| Frontend | React 19, React Router 7, Vite |
| Build | Maven, Docker Compose, GitHub Actions |

## Quick start

```bash
# Backend tests (no database needed)
mvn test

# Full stack
export APP_JWT_SECRET=$(python3 -c "import secrets,base64;print(base64.b64encode(secrets.token_bytes(32)).decode())")
export DB_PASSWORD=dev-password
docker compose up --build          # API on :8080

# Frontend
cd frontend && npm install && npm run dev   # UI on :5173
```

The API container runs Flyway migrations at startup and validates the schema with `ddl-auto: validate`. Use a new `APP_JWT_SECRET` outside local development.

## API

| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | none | Returns a JWT; 409 on duplicate email |
| GET | `/api/auth/me` | JWT | Current identity |
| GET | `/api/products`, `/api/categories` | none | Supports `query`, `categoryId`, `sort`, `page`, `size` |
| POST/PUT/DELETE | `/api/products/**`, `/api/categories/**` | Seller/Admin | 403 otherwise |
| GET | `/api/products/analytics/summary` | Seller/Admin | Catalog aggregates |
| GET/POST/DELETE | `/api/cart`, `/api/cart/items/**` | JWT | 409 when quantity exceeds stock |
| POST | `/api/orders` | JWT | Body includes `idempotencyKey`; 201 new, 200 on retry |
| GET | `/api/orders`, `/api/orders/{id}` | JWT | Owners see their orders |
| POST | `/api/orders/{id}/cancel` | Owner/Admin | Only from `PENDING` or `CONFIRMED` |
| PATCH | `/api/orders/{id}/status` | Seller/Admin | 422 on illegal transition |
| POST | `/api/orders/{id}/payment/simulate` | Owner/Staff | `{"outcome": "SUCCESS"}` or `{"outcome": "FAILED"}` |

Errors use one shape: `{timestamp, status, error, message, path}`.

## Architecture

```
React (Vite, :5173) -- JSON + Bearer JWT --> Spring Boot (:8080) --> PostgreSQL (:5432)
                                               Controller -> Service (@Transactional) -> Repository
                                               GlobalExceptionHandler -> uniform error JSON
```

Design notes:

- Stateless JWT, no session store.
- Pessimistic row locks on stock during checkout prevent overselling under concurrency.
- Order items store a price snapshot, so later catalog edits do not rewrite order history.
- Flyway owns the schema; Hibernate only validates it.

## Layout

```
src/main/java/com/forgecommerce/
  auth/        users, registration, login, JWT
  catalog/     categories, products, inventory
  order/       cart, orders, payments
  security/    filter, token service, user details
  common/      error handling
  config/      security configuration
  health/      liveness endpoint
src/main/resources/db/migration/   V1 users, V2 catalog, V3 orders
frontend/src/                      pages, auth context, API client
```

## Testing

27 tests cover JWT handling, authentication and authorization, catalog RBAC and pagination, stock rules, checkout, idempotency, oversell prevention, payment outcomes, and order transitions. `mvn test` uses H2 and needs no external services. CI runs the same suite on every push.

## Limitations

No refresh-token rotation, login rate limiting, or Testcontainers-backed database tests yet. Payments are simulated; there is no payment provider integration. There is no production deployment in this repository.

## License

MIT. See [LICENSE](LICENSE).
