# SEKKIN.LK backend

Spring Boot API for a cosmetics store, with a React storefront in the separate frontend repository. The project includes customer accounts, products/categories, carts, COD orders, reviews, image management and administrator operations.

## Run the submission demo

Requires Java 21 and PowerShell. Maven is supplied by the wrapper; the first run needs internet access to download dependencies.

```powershell
.\start-demo.ps1
```

This starts the API at `http://localhost:8080`, prints a temporary administrator email/password and adds three clearly labelled sample products. The explicit `demo` Maven/Spring profiles use an in-memory H2 database. Accounts, carts, orders and products disappear when the backend stops. The demo does not need PostgreSQL, Docker, Cloudinary or hosted services.

Start the frontend separately with `npm ci` and `npm run dev:demo` from its repository root (`Frontend/frontend` in this workspace). Open `http://localhost:5173`; sign in with the printed account and visit `/admin`. Register a separate customer for the cart/order demonstration.

See [the demo and submission guide](docs/DEMO_SUBMISSION.md) for the walkthrough, validation results, submission checklist and remaining limitations.

## Implemented features

- Registration/login, BCrypt passwords, JWT authentication, `ADMIN`/`USER` authorization, account disabling and session revocation.
- Product/category operations, stock and discounts, product images and review eligibility.
- Customer cart operations and server-calculated pricing.
- Cart and direct COD checkout, a single LKR 350 delivery fee, coupon validation and UUIDv4 retry protection.
- Customer order history; administrator acceptance, fulfilment transitions, cancellation and separate COD collection recording.
- Validation, request throttling and isolated service/security/persistence tests.

## PostgreSQL configuration

Use [.env.example](.env.example) as a variable checklist. Spring Boot does **not** automatically load that file: set the values in your IDE or process/hosting environment. A private server-only `JWT_SECRET` of at least 32 bytes is required. No production administrator or default signing secret is supplied.

Run `./mvnw spring-boot:run` (Windows: `.\mvnw.cmd spring-boot:run`) after configuring the database. For an explicitly disposable local database, `JPA_DDL_AUTO=update` can create the initial schema. Existing databases default to validation.

Review [the additive order/session migration](docs/migrations/2026-11-16-order-session-safety.sql) against the intended database before deploying the updated entities. It is not executed at startup. Do not use `create` or `create-drop` with production data.

The existing Docker Compose setup is local development configuration with local database credentials. Set `JWT_SECRET` before `docker compose up --build`; checkout remains disabled unless explicitly enabled. The submission demo uses the separate script above.

`PORT` is supplied by Render (local default `8080`). `CORS_ALLOWED_ORIGINS` accepts explicit comma-separated origins. Cloudinary credentials remain on the backend; see [Cloudinary setup](CLOUDINARY_SETUP.md).

Ordering defaults to disabled outside the explicit demo (`CHECKOUT_ENABLED=false`). Enable it only after reviewing the matching frontend, schema, credentials and checkout behaviour. CARD, KOKO and PayHere are not integrated; the retained order flow is COD. Administrator acceptance and cash collection are separate operations.

## Verification

```powershell
.\mvnw.cmd -B test
.\mvnw.cmd -B package
```

The normal test suite uses isolated fixtures. Real PostgreSQL tests additionally require `SEKKIN_RUN_POSTGRES_TESTS=true` and `SEKKIN_POSTGRES_TEST_URL=jdbc:postgresql://127.0.0.1:PORT/sekkin_release_test`, with an empty disposable database marked `SEKKIN_DISPOSABLE_RELEASE_VERIFICATION`. CI provisions it. H2 demo success does not establish PostgreSQL migration/concurrency correctness.

## Main API routes

| Route | Purpose |
| --- | --- |
| `POST /api/auth/register`, `POST /api/auth/login` | Customer accounts |
| `GET /api/products`, `GET /api/categories` | Public catalog |
| `GET /api/cart`, `POST /api/cart/items` | Authenticated customer cart |
| `POST /api/orders` | Authenticated cart checkout |
| `POST /api/orders/direct` | Direct COD order request |
| `GET /api/orders` | Current customer's orders |
| `GET /api/admin/dashboard`, `GET /api/admin/orders` | Administrator reporting |
| `POST /api/admin/orders/{id}/accept` | Administrator order acceptance |
| `POST /api/admin/orders/{id}/cod-collection` | Record verified COD collection |

Order creation requires an `Idempotency-Key` UUIDv4 header. The frontend manages retry keys.
