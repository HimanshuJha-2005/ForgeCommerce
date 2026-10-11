# ForgeCommerce

![backend](https://github.com/HimanshuJha-2005/ForgeCommerce/actions/workflows/backend.yml/badge.svg)

Full-stack commerce platform — **Spring Boot + PostgreSQL + React** — with JWT authentication,
role-based access control, a searchable product catalog, cart, transactional checkout, and
simulated payments.

## Screenshots

| Storefront | Order detail | Seller dashboard |
|---|---|---|
| ![Storefront](docs/screenshots/storefront.png) | ![Order](docs/screenshots/order.png) | ![Seller](docs/screenshots/seller.png) |

## Features

- **Auth** — registration, login, JWT (1h expiry), BCrypt hashing; roles `CUSTOMER` / `SELLER` / `ADMIN` enforced server-side
- **Catalog** — product listing with search, category filter, sorting and pagination; admin-only categories; seller product CRUD and stock management
- **Seller analytics** — catalog summary (counts, averages, per-category breakdown) computed with the Streams API
- **Cart** — per-user cart with live stock validation
- **Orders** — checkout runs as a single transaction: lock stock rows → validate → decrement → create order, price-snapshotted items and payment → clear cart; idempotency keys make retries safe
- **Order lifecycle** — `PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED` plus `CANCELLED`; illegal transitions rejected
- **Payments (simulated)** — success/failure simulation driving order confirmation or cancellation with automatic restock
- **Storefront (React)** — browsing, cart, checkout, order history, seller dashboard; protected routes, loading/error/empty states, responsive layout

## Tech stack

| Layer | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 3.2 (Web, Security, Data JPA, Validation), JJWT |
| Database | PostgreSQL 16, Flyway migrations, H2 for tests |
| Frontend | React 18, React Router, Vite |
| Build & infra | Maven, Docker Compose, GitHub Actions |

## Quick start

```bash
# Backend tests (no infrastructure needed)
mvn test

# Full stack with Postgres
export APP_JWT_SECRET=$(python3 -c "import secrets,base64;print(base64.b64encode(secrets.token_bytes(32)).decode())")
export DB_PASSWORD=dev-password
docker compose up --build        # API on :8080

# Frontend
cd frontend && npm install && npm run dev   # UI on :5173
```

## API reference

| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | — | Returns JWT; 409 on duplicate email |
| GET | `/api/auth/me` | JWT | Current identity |
| GET | `/api/products`, `/api/categories` | — | `query`, `categoryId`, `sort`, `page`, `size` |
| POST / PUT / DELETE | `/api/products/**`, `/api/categories/**` | Seller/Admin | 403 otherwise |
| GET | `/api/products/analytics/summary` | Seller/Admin | Aggregated catalog stats |
| GET / POST / DELETE | `/api/cart`, `/api/cart/items/**` | JWT | 409 when quantity exceeds stock |
| POST | `/api/orders` | JWT | Body: `idempotencyKey`; 201 new, 200 on retry |
| GET | `/api/orders`, `/api/orders/{id}` | JWT | Owners see own orders |
| POST | `/api/orders/{id}/cancel` | Owner/Admin | Only from `PENDING` / `CONFIRMED` |
| PATCH | `/api/orders/{id}/status` | Seller/Admin | 422 on illegal transition |
| POST | `/api/orders/{id}/payment/simulate` | Owner/Staff | `{"outcome": "SUCCESS" \| "FAILED"}` |

All errors return a uniform JSON body: `{timestamp, status, error, message, path}`.

## Architecture

```
React (Vite, :5173) ──JSON + Bearer JWT──▶ Spring Boot (:8080) ──▶ PostgreSQL (:5432)
                                             │  Controller → Service (@Transactional) → Repository
                                             └── GlobalExceptionHandler → uniform error JSON
```

Key design points: stateless JWT (no session store); pessimistic stock locking at checkout so
concurrent orders can't oversell; order items snapshot prices so catalog edits never rewrite
history; Flyway-versioned schema validated at startup (`ddl-auto: validate`).

## Project structure

```
src/main/java/com/forgecommerce/
├── auth/        # users, registration, login, JWT
├── catalog/     # categories, products, inventory
├── order/       # cart, orders, payments
├── common/      # global exception handling
├── config/      # security configuration
└── health/      # liveness probe
src/main/resources/db/migration/   # V1 users → V2 catalog → V3 orders
frontend/src/    # pages, auth context, API client
```

## Testing

27 tests, run in CI on every push: JWT unit tests, auth API tests (success, duplicate,
validation, authorization), catalog tests (RBAC, pagination, stock rules, analytics) and
order tests (checkout, idempotency, oversell, payment flows, transitions). `mvn test` needs
no external services.

## Limitations & roadmap

Not yet covered: refresh-token rotation, login rate limiting, Testcontainers-based DB tests,
full Postman collection, production deployment. Planned next.
