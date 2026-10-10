# ForgeCommerce — Enterprise Order Management & Commerce Platform

Full-stack commerce platform: Spring Boot + PostgreSQL backend, React storefront.
Covers auth, catalog, cart, transactional orders, simulated payments.

> Honesty note: no benchmarks, coverage %, or deployment claims are made — none measured.
> Known gaps are listed in §17 rather than hidden.

## Features

**Auth** — `POST /api/auth/register` (201 + JWT) · `POST /api/auth/login` (200) · `GET /api/auth/me`
Roles CUSTOMER / SELLER / ADMIN enforced server-side via `@PreAuthorize` + URL rules. BCrypt hashing.

**Catalog** — public product listing with search, category filter, sort (whitelisted fields), pagination;
product detail; ADMIN-only categories; SELLER/ADMIN product CRUD + stock updates (negative rejected);
seller analytics summary (Streams `groupingBy` + reductions).

**Cart** — per-user cart, add/remove with live stock validation (409 on oversell), totals via streams.

**Orders** — checkout is ONE `@Transactional` unit: pessimistic-lock stock rows → validate →
decrement → create order + price-snapshotted items + PENDING payment → clear cart. Any failure rolls back.
Idempotency key per user: retries return the original order (200) instead of double-charging.
Lifecycle PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED, plus CANCELLED, with illegal
transitions rejected (422). Cancel/payment-failure restores stock.

**Payments (simulation)** — `POST /api/orders/{id}/payment/simulate {SUCCESS|FAILED}` stands in for a
gateway webhook: SUCCESS → payment SUCCESS + order CONFIRMED; FAILED → payment FAILED + order CANCELLED
+ restock. Payments move exactly once (duplicate webhook → 422); references are unique.

**Frontend** — React (Router, hooks, no global-state lib): public storefront with search/filter/sort/pagination,
product detail, auth forms with client validation, protected cart/checkout/orders routes, seller dashboard
(categories, products, stock), order detail with cancel / payment simulation / staff status buttons,
loading + error + empty states throughout, responsive CSS.

## Architecture

```
React (Vite dev :5173, proxy → :8080)
  ↓ JSON + Bearer JWT
SecurityConfig (stateless) → Controllers → Services (@Transactional) → Repositories → PostgreSQL
  ↓                                              ↓ Flyway V1→V3, ddl-auto: validate
GlobalExceptionHandler → ApiError{timestamp,status,error,message,path}
```

No entities leak through the API (records as DTOs); no business logic in controllers.

## Tech stack

Java 21 LTS · Spring Boot 3.2.5 (Web, Validation, Security, Data JPA) · JJWT · PostgreSQL 16
(runtime) / H2 (tests) · Flyway · Maven · React 18 + Router + Vite · Docker Compose.

## Database

`users` → `carts` (one per user) → `cart_items` · `categories` → `products` → `inventory` (1:1, `@Version`) ·
`orders` (+`uq_orders_user_key`) → `order_items` (price snapshots) · `payments` (unique ref, 1:1 order).
CHECK constraints on role/price/qty/status; FKs with cascading deletes on line tables.
Indexes (each documented in its migration): category listing, active-only partial, inventory join,
cart/order line lookups, user history. Name `ILIKE '%q%'` deliberately unindexed (b-trees can't serve
leading-wildcard; trigram only if measured). See `src/main/resources/db/migration/V1–V3__*.sql`.

## API quick reference

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/api/health` | no | liveness |
| POST | `/api/auth/register`, `/api/auth/login` | no | 201/200 + JWT; 409 duplicate; 400 validation |
| GET | `/api/auth/me` | JWT | 401 without token |
| GET | `/api/categories`, `/api/products…` | no | search/filter/sort/page |
| POST/PUT/DELETE | `/api/categories` | ADMIN | 403 otherwise |
| POST/PUT/DELETE | `/api/products…`, `PUT …/stock` | SELLER/ADMIN | 400 bad price/qty |
| GET | `/api/products/analytics/summary` | SELLER/ADMIN | Streams aggregation |
| GET/POST/DELETE | `/api/cart`, `/api/cart/items…` | JWT | 409 oversell |
| POST | `/api/orders {idempotencyKey}` | JWT | 201 new / 200 retry |
| GET | `/api/orders`, `/api/orders/{id}` | JWT | owners see own; staff see all |
| POST | `/api/orders/{id}/cancel` | owner/ADMIN | PENDING/CONFIRMED only |
| PATCH | `/api/orders/{id}/status` | SELLER/ADMIN | 422 illegal transition |
| POST | `/api/orders/{id}/payment/simulate` | owner/staff | SUCCESS/FAILED |

Error body: `{"timestamp","status","error","message","path"}` — never a stack trace.
Postman: `postman/forgecommerce-m1.postman_collection.json` (auth flows; catalog/order flows in M5).

## Key decisions

- Stateless JWT (no session store; horizontal-scale friendly) — tradeoff: no instant revocation; refresh rotation planned, not claimed.
- Pessimistic stock locks at checkout (correctness over throughput at this scale) + `@Version` on inventory for admin writes; optimistic-only was rejected because two checkouts could both pass validation.
- Price snapshots on order items (history immutable); idempotency keys scoped per user.
- H2 tests / Postgres runtime (fast hermetic `mvn test`; dialect risk noted, Testcontainers planned).
- 409 stock conflicts, 422 state transitions, 401 vs 403 split deliberately.

## Security

Env-only secrets (compose fails fast without `APP_JWT_SECRET`); BCrypt; JWT expiry 1h; role checks
server-side (frontend guards are UX only); no sensitive logging; no secrets in git.
Gaps (not hidden): no rate limiting, no lockout, registration accepts any role incl. ADMIN (local-dev
convenience — restrict in real deploy), payment simulator is owner-callable (a real webhook would be
gateway-signed).

## Testing — 27 tests, all green (`mvn test`, no infra)

`JwtServiceTest` (3) · `AuthApiTest` (9: 201/409/401s/400s/role-required) · `CatalogApiTest` (8: RBAC 403/401,
pagination+sort, search+filter, 404, negative stock, analytics) · `OrderApiTest` (7: full checkout,
idempotent retry, oversell 409, payment fail→cancel+restock, illegal 422, cancel restock, auth).
No coverage % claimed (JaCoCo not wired).

## Run locally

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$env:Path = "C:\Tools\apache-maven-3.9.9\bin;$env:JAVA_HOME\bin;" + $env:Path
cd trinity-portfolio\forgecommerce
mvn test
$env:APP_JWT_SECRET = "<base64 32+ bytes>"; $env:DB_PASSWORD = "dev-only-password"
docker compose up --build        # Postgres + API on :8080
cd frontend; npm install; npm run dev   # storefront on :5173 (proxies /api → :8080)
```

Env: `APP_JWT_SECRET` (required) · `APP_JWT_EXPIRATION_MS` (default 1h) · `DB_URL/DB_USER/DB_PASSWORD`.

## Deployment (documented, NOT deployed)

Stateless API container + managed Postgres (e.g., ECS/Fargate + RDS); JWT needs no session affinity.
Status: not deployed — no claims made.

## Known limitations → next

No refresh tokens · no rate limiting/lockout · H2-vs-Postgres test drift · open role registration ·
name search unindexed · analytics is in-memory (fine now; SQL aggregation at scale) · Postman covers
auth only · frontend has no seller order queue view.

## Interview mapping (selected)

| Q | A (file) |
|---|---|
| Why stateless JWT? | `security/JwtService.java` — scale; tradeoff: revocation |
| How do you stop overselling? | `order/OrderService.java` — single TX + `PESSIMISTIC_WRITE` + pre-lock validation; `@Version` on `Inventory` |
| What if checkout is retried? | `uq_orders_user_key` + return-original (200) — `OrderService.placeOrder` |
| Why snapshot prices? | `order/OrderItem.java` — history immutable under catalog edits |
| 401 vs 403 vs 409 vs 422? | `config/SecurityConfig.java`, `common/exception/GlobalExceptionHandler.java` |
| Streams usage — real? | `CatalogService.summarize()` (`groupingBy` + downstream reductions), cart totals (`map`/`reduce`) |
| `Optional`? | Repository finders; absence is explicit |
| N+1? | `findAllActiveWithCategory` join-fetch; per-row stock lookup flagged for M5 |
| Flyway + validate? | `db/migration/V1–V3` — reviewed, versioned, drift-proof startup |
