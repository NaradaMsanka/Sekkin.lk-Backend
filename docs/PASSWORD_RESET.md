# SEKKIN.LK password recovery

This change adds password recovery to the existing JWT authentication system. No live database, SMTP account or deployed website is modified by the local implementation. A real Resend message and production reset still require operator verification.

## Audit and design

- Backend: Spring Boot 3.3.3 / Java 21, Spring Security, custom JWT, BCrypt, JPA/PostgreSQL. Registration and login already exist; no prior recovery endpoint/table/mail service was found.
- `User.sessionVersion` is checked against the JWT on every authenticated request. Reset increments it; the next request with any prior JWT fails authentication. Requests already authenticated before the reset commits are not retroactively cancelled.
- No Flyway/Liquibase is installed. Existing deployments use reviewed manual SQL and `ddl-auto=validate`; the new migration follows that mechanism. Compose now also defaults to `validate` rather than silently altering production tables.
- The supplied workflow builds/tests/Docker images; it contains no VPS deployment step. The actual private panel/process manager cannot be inferred from the checkout.
- Password policy: reset preserves the current signup UI minimum of 6 characters, plus the existing backend BCrypt rule of at most 72 UTF-8 bytes, well-formed Unicode and nonblank input. Legacy backend registration alone allows shorter passwords; its behavior is intentionally unchanged.

The implementation follows [OWASP Forgot Password guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html), [Resend SMTP settings](https://resend.com/docs/send-with-smtp), and [Spring Boot mail timeout guidance](https://docs.spring.io/spring-boot/3.3/reference/io/email.html).

## API and browser flow

`POST /api/auth/forgot-password` accepts `{"email":"customer@example.com"}`. Public, no JWT required. The successful response is always:

```json
{"message":"If an account with that email exists, you will receive a password reset link shortly."}
```

The same response is returned for unknown, disabled and ambiguously duplicated case-variant email addresses, account-level throttling, queue saturation and SMTP failure. Work runs in a bounded background queue, without synchronous account lookup or SMTP timing in the HTTP response. Global disabled configuration returns the same generic 503 for every address. Generic confirmation confirms receipt of the request, not delivery.

Email contains `https://www.sekkin.lk/reset-password#token=...`. The fragment is not sent to Vercel, the reverse proxy, or API access logs. React captures it in memory, removes it from the address bar, and submits it only in the HTTPS reset POST body. Reloading after capture requires reopening the email link. No token is stored in local/session storage, analytics, or logs. The recovery pages use local assets and no storefront chrome, plus early `no-referrer` protection and noindex/no-store headers.

`POST /api/auth/reset-password` accepts `token`, `newPassword`, and `confirmPassword`. Success returns a message, never a JWT. Invalid, used and expired links share a generic 400 response; mismatched/invalid passwords are rejected without consuming the link. React clears stale local login data after success and returns to Login with a confirmation.

Tokens contain 256 random bits from `SecureRandom`; PostgreSQL stores only a SHA-256 digest. Expiry is exactly 20 minutes from creation. A row lock on the owning user serializes every reset, including different links for the same account. Password hashing, session-version increment and consumption of all outstanding tokens commit together. Requesting a new email does not invalidate previous unexpired links. A password-changed notice is queued only after a successful commit; notification failure cannot undo the reset.

Limits per single backend instance:

| Operation | Limit |
| --- | --- |
| Forgot-password by IP | 10 requests / 15 minutes; excess receives 429 |
| Forgot-password by normalized email | 3 requests / hour, across IPs; excess keeps generic 200 |
| Reset-password by IP | 20 requests / 15 minutes; excess receives 429 |

Limits apply before knowing whether the email/token belongs to a user. Bounded hashed-key memory follows the existing `RequestThrottle`. Counters reset on process restart; use a shared gateway/limiter before adding backend replicas. The email queue is in memory (2 workers, 32 queued jobs), not a durable mail outbox; a crash may lose a queued request. The customer can request another link after throttling permits. Hourly cleanup removes token rows whose expiry is over one day old.

## Backend runtime settings

Set these on the **backend process/container**, not the Vercel frontend. The Resend API key is the SMTP password, as specified by Resend. Do not paste it into source, chat, command-line arguments, screenshots or diagnostic logs.

| Variable | Value |
| --- | --- |
| `PASSWORD_RESET_ENABLED` | `true` after migration and SMTP configuration; default `false` |
| `PASSWORD_RESET_FRONTEND_URL` | `https://www.sekkin.lk` (trusted HTTPS origin, no path/query/fragment) |
| `PASSWORD_RESET_FROM` | `no-reply@sekkin.lk` |
| `SMTP_HOST` | `smtp.resend.com` |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME` | `resend` |
| `SMTP_PASSWORD` | Your Resend API key, stored as a runtime secret |
| `SMTP_CONNECTION_TIMEOUT_MS` | `5000` |
| `SMTP_READ_TIMEOUT_MS` | `5000` |
| `SMTP_WRITE_TIMEOUT_MS` | `5000` |
| `JPA_DDL_AUTO` | `validate` |
| `CORS_ALLOWED_ORIGINS` | Include `https://www.sekkin.lk` and any other explicitly approved storefront origin |

Keep existing database/JWT/checkout/image settings unchanged. Spring Boot binds the mail variables through `spring.mail.*` and recovery variables through `app.password-reset.*` in `application.yml`. STARTTLS is enabled **and required**, SMTP authentication and TLS hostname checks are enabled, and connection/read/write timeouts are bounded. Enabling recovery with missing SMTP credentials or an unsafe frontend origin fails startup with a configuration-only error. Do not enable JavaMail protocol debug, request-body logging or proxy logging of POST bodies.

Spring Boot/JAR does **not** read `.env` automatically. This repository's Compose file explicitly forwards the variables; a panel's environment page must inject them into the running backend app. `docker compose restart` alone does not replace a container's environment.

## Database migration

Use [2026-11-22-password-reset.sql](migrations/2026-11-22-password-reset.sql). It adds only `password_reset_tokens`, its user foreign key, expiry/user indexes and integrity checks. It does not rewrite users, passwords, roles, products, orders or other records.

Before running it:

1. Identify the actual application database and schema/search path. Stop if the target is uncertain. Back it up and verify the restore procedure.
2. Inspect `users.id` (bigint), `users.session_version` (bigint, nonnull), `users.enabled` (boolean, nonnull), and any existing reset-table/index definitions. `IF NOT EXISTS` does not verify compatibility. If session columns are absent, review the existing session-safety migration separately; do not blindly run unrelated order changes.
3. Rehearse the migration against a restored disposable PostgreSQL copy. Verify schema validation succeeds and existing data counts match.
4. Apply the additive reset migration before starting the new backend with `validate`. Keep that validation enabled. Never use `create`, `create-drop`, database recreation or a volume deletion in production.

If your VPS actually uses the repository's Compose service names and database container, these commands run from the Backend directory in the VPS/panel terminal. They are instructions for the operator; Codex has not run them on the VPS. Substitute your reviewed existing database/service names if the deployment differs.

```bash
# Save a backup inside the existing PostgreSQL container; copy it to secure backup storage.
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/sekkin-before-password-reset.dump'
docker cp cosmetic-postgres:/tmp/sekkin-before-password-reset.dump ./sekkin-before-password-reset.dump

# During the reviewed maintenance window, stop only the backend; keep PostgreSQL and its volume.
docker compose stop backend
docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docs/migrations/2026-11-22-password-reset.sql
```

The script uses a transaction and bounded lock/statement timeouts. On failure, stop and inspect the schema/lock issue; do not bypass it with automatic DDL. Do not publish the backup (it contains private customer data).

## VPS deployment and restart

The private panel's app manager and actual process definition are not available locally. Oracle VPS controls alone do not inject environment values into a JVM or Docker container.

If the panel has a backend app's **Environment/Secrets** page, add the variables above there, keeping `SMTP_PASSWORD` secret. Upload/build the reviewed backend revision, apply the SQL through the database console or approved panel terminal, then use **Deploy/Recreate** (or its equivalent) so the app receives the new environment. Keep its existing database, volume, network, JWT secret and app identity. A generic VPS reboot is not a deployment.

For the checked-in Compose method, place the settings in its existing private environment mechanism (for example an ignored backend `.env` with restrictive permissions), then:

```bash
docker compose build backend
docker compose up -d --no-deps --force-recreate backend
docker compose logs --tail=80 backend
```

Inspect only sanitized startup status; never post environment dumps, `docker inspect` output, SMTP debug output or reset links. The Compose file is documented as a local development template; do not replace an existing production Compose file or database credentials with its defaults. Merge the new backend environment bindings into the actual production definition.

If the app is an existing JAR/systemd service, deploy the new JAR through its current mechanism, add these settings to the service's existing private `EnvironmentFile` or panel environment, then restart that **existing** unit. Its unit name/path is not present in this repository; identify it before using `systemctl restart`. Run `systemctl daemon-reload` only if you changed the unit itself. No new process manager or public port is required.

Allow outbound DNS and TCP 587 from the VPS/container to Resend; do not expose SMTP inbound. If outbound SMTP is blocked by the VPS/provider, request an approved egress rule. Resend's verified sender and API-key sending permissions remain operator checks.

### Reverse-proxy IP attribution

The limiter uses `request.getRemoteAddr()` and does not trust arbitrary caller-supplied `X-Forwarded-For`. If the backend sits behind Nginx/a panel proxy, verify in staging that separate clients are attributed separately. Otherwise all visitors may share the proxy's rate limit.

If using Tomcat native forwarding, configure `SERVER_FORWARD_HEADERS_STRATEGY=native` and `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` to a reviewed regex matching **only your actual trusted proxy address(es)**. The proxy must overwrite untrusted forwarding headers, and direct public access to the backend must be blocked. These standard Spring Boot settings must reach the actual JVM/container; if using Compose, add them to its backend environment explicitly. Do not use `.*`, trust all private networks indiscriminately, or enable forwarding until proxy addresses and edge header handling are known. Verify spoofed headers cannot bypass throttles. There is no evidence of the private panel's proxy configuration locally.

## Frontend deployment

After backend schema/configuration and staging tests are ready:

1. Run `npm ci`, `npm test`, and `npm run build` in `Frontend/frontend`.
2. Keep `VITE_API_BASE_URL` pointing at the existing HTTPS backend or approved same-origin proxy. Add no SMTP secret to any `VITE_*` variable or Vercel frontend environment.
3. The owner reviews, commits and pushes the frontend revision through the existing repository. In Vercel select the frontend project and deploy that revision using its existing workflow. Redeploying an old commit does not include local changes.
4. Confirm direct navigation to `/forgot-password` and `/reset-password` serves the React app. Existing SPA rewrites are preserved. Check `Referrer-Policy: no-referrer` and no-store/noindex protections on recovery routes.

No commit, push or deployment is performed automatically by this task.

## Production verification required from the operator

Use an owner-controlled test account, never a customer's account. Do not paste its reset link/token into logs, support chats or screenshots.

- Confirm normal signup/login, admin authorization and ordinary shopping still work.
- Request a reset for the test account and a nonexistent address; both should receive the same generic API confirmation. Check Resend's Emails dashboard, inbox/spam and actual message content. SMTP acceptance alone is not proof of inbox delivery.
- Open the received email's button; verify HTTPS, `/reset-password`, and that the token disappears from the address bar. Check the request/referrer/network logs do not contain it in URLs or headers.
- Mismatched passwords should fail locally. Reset successfully with matching valid passwords. Confirm success returns to Login without signing in automatically.
- Verify old password rejection and new password login. An old JWT must fail on the next protected request. Confirm the password-changed notification arrives.
- Reopen the used link; it must fail. Test another link after 20 minutes; it must fail. Complete one of two outstanding links; the other must then fail.
- Verify IP/email throttles and trusted proxy behavior using controlled staging traffic. Do not flood live customer email addresses.
- Simulate SMTP rejection in staging; responses must stay generic with no SMTP error, email existence or credential values exposed. Check generic server health warnings and Resend status directly.

Real delivery, private-panel runtime configuration and production end-to-end verification remain operator actions until recorded. Local mocks do not establish them.

## Implementation files and local verification

Created backend files (paths below `src/main/java/com/cosmetic/ecommerce/`):

- `config/PasswordResetProperties.java`, `config/PasswordResetConfiguration.java`: trusted origin/sender validation, bounded worker, UTC clock and enabled SMTP configuration checks.
- `controller/PasswordResetController.java`: public recovery endpoints and safe responses.
- `dto/ForgotPasswordRequest.java`, `dto/ResetPasswordRequest.java`: bounded validated inputs without credential-bearing `toString` methods.
- `entity/PasswordResetToken.java`, `repository/PasswordResetTokenRepository.java`: hash-only persistence, token consumption and expiry cleanup.
- `service/PasswordResetService.java`, `service/PasswordResetDispatcher.java`, `service/PasswordResetMailer.java`: atomic reset/session revocation, bounded asynchronous mail and branded email.
- `src/test/java/com/cosmetic/ecommerce/security/PasswordResetPersistenceTest.java`, `PasswordResetDeliveryTest.java`, `PasswordResetHttpTest.java`: persistence/concurrency, SMTP safety, real authentication and HTTP checks.
- `docs/migrations/2026-11-22-password-reset.sql` and this guide.

Modified backend files:

- `pom.xml`, `src/main/resources/application.yml`, `.env.example`, `docker-compose.yml`, `README.md`.
- `config/SecurityConfig.java`, `config/AbuseRateLimitFilter.java`, `repository/UserRepository.java` under the Java package above.
- `src/test/java/com/cosmetic/ecommerce/postgres/PostgresReleaseIntegrationTest.java`.

Created frontend files (paths below `Frontend/frontend/` in the shared workspace):

- `src/pages/ForgotPassword.jsx`, `src/pages/ResetPassword.jsx`, `src/pages/PasswordRecoveryLayout.jsx`.
- `src/content/passwordPolicy.js`, `scripts/password-recovery.test.mjs`.

Modified frontend files: `src/App.jsx`, `src/pages/Login.jsx`, `src/services/api.js`, `index.html`, `vercel.json`, `README.md`.

Verified locally on 22 November 2026:

- `mvn -B test`: 224 executed tests passed; PostgreSQL-gated tests were skipped in this run.
- `mvn -B -Dtest=PostgresReleaseIntegrationTest test` with the explicit disposable database variables: 23 passed, no skips, against PostgreSQL 17.12 on loopback only. A fresh synthetic schema received both manual migrations twice, then Hibernate `validate` passed. Same-token/different-token reset races each ran three times with one winner, and real old/new password login and JWT revocation passed.
- `npm test`: 43 passed, including 6 password-recovery validation/token tests.
- `npm run build`: passed; existing bundle-size advisory remains.
- `mvn -B -DskipTests package`: deployable backend JAR built after the test runs above (packaging did not rerun tests).
- Mocked browser checks: desktop and 320/390/768px, generic known/unknown confirmations, loading/errors/rate limits, invalid links, confirmation and byte-length validation, password visibility, token cleanup, keyboard skip navigation, no saved JWT on recovery requests, and successful return to login without automatic sign-in. No page errors.

Backend evidence is in ignored `target/password-reset-full-test.log` and `target/password-reset-postgres-test.log`; browser report/screenshots are in the shared workspace's `ui-review/password-recovery`. All test email sending was mocked or disabled. The disposable PostgreSQL server and owned frontend preview were stopped. No production data, push or deployment was performed.
