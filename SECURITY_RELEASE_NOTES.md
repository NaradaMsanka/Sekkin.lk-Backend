# Local security release notes - operational approval phase

**Current final release preparation:** [the final report](../Frontend/frontend/docs/FINAL_PRODUCTION_COMPLETION_REPORT.md) records **10 unresolved messages**, contacts/temporary-catalog approval true, factual policy/security approval false. Maintained Boot 4.1.2 packaging passed **209 tests**, including **14 real PostgreSQL** cases; the exact scanned non-root runtime passed **15 actual HTTP/PostgreSQL checks**. Java scan has zero matches; OS remediation leaves **one BusyBox Medium advisory /three package matches**, zero Critical/High. Actual hosted settings, exposed-key/account containment and final controlled source-image verification remain operator work. No production mutation occurred. **The 36-message/192-test/old-dependency description below is historical.**

Verified 16 November 2026. **Local changes only: no push, merge, deployment, runtime secret rotation, account reset or production data change.** The frontend has **36 readiness messages**, down from 61 after owner-approved procedures were applied. All four full flags remain false.

Full offline `.\mvnw.cmd -o -B package` passes **192 tests, zero failures/errors/skips**. Tests use isolated MVC/mocks and replaced H2 only; they do not establish hosted PostgreSQL, a Render deployment or a scanned container. Frontend tests pass 43/43 and intercepted browser checks 77/77.

This phase adds sorted product locks/account-cart serialization, refreshed cart prices, persistent UUIDv4 creation retries with unique hashes and intent fingerprints, displayed-total/cart-content consent and lossless monetary persistence guards. New cart requests require a delivery phone. Requests are PENDING until audited staff acceptance; transitions are limited, known new reservations restock once on cancellation, and legacy unknown reservation data stays untouched. Explicit ADMIN COD collection requires delivered COD, the exact saved amount and a nonsecret reference; operator/time are recorded. Status changes alone never mark PAID. Protected administrator reports count only audited COD collections; customer JSON omits internal retry/audit metadata.

JWT authentication now checks the current session version, enabled state and database role. All-device logout and guarded per-account revocation/disable routes invalidate account sessions. Bounded single-process throttles cover authentication/creation; verify proxy attribution and shared limits before scaling. Password inputs above 72 UTF-8 bytes or containing malformed Unicode are rejected before BCrypt, with no truncation or invented minimum policy.

**Deployment prerequisites remain:** review/rehearse the additive [manual migration](docs/migrations/2026-11-16-order-session-safety.sql), including its actual unique index, then stage schema validation and real concurrent PostgreSQL tests using [the release preparation guide](docs/ORDER_SESSION_RELEASE_PREPARATION.md). No SQL was executed against a hosted database. Global exposed-key rotation, administrator/credential/history review, exact CORS/proxy/logging/regions, Docker/image/JDK/runtime verification and actual courier settlement evidence remain outstanding. Ordinary logout clears one browser; clearing local state is not global revocation.

**Dependency blocker:** the packaged runtime still contains Boot 3.3.3, Security 6.3.3, Framework 6.1.12 and Tomcat 10.1.28. Complete a maintained dependency migration and current vulnerability/container scan before release. The password guard does not patch the library advisory. The frontend [dependency review](../Frontend/frontend/docs/DEPENDENCY_SECURITY_REVIEW.md), [current report](../Frontend/frontend/docs/OWNER_APPROVAL_PHASE_REPORT.md) and [Render runbook](../Frontend/frontend/docs/RENDER_SECURITY_RUNBOOK.md) contain evidence and exact coordinated release requirements.

## Historical phase-2 record

The sections below describe the earlier 134-test revision and its then-outstanding work. They are retained as history; the current phase above supersedes them.

## Runtime requirements before any manual Render release

- `JWT_SECRET` is mandatory with at least 32 UTF-8 bytes. Generate a new independent high-entropy value; this code uses the literal UTF-8 string, without Base64 decoding. It contains no embedded fallback. Earlier signing material can remain in Git history and deployed instances until an authorized rotation.
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` are now required. There are no embedded localhost/database-credential defaults. Verify actual Supabase JDBC/TLS configuration without printing values.
- `JPA_DDL_AUTO` defaults to **validate**. Check any Spring/profile/environment overrides so Production does not silently use update/create/create-drop. Stage schema validation and prepare reviewed migrations for existing discrepancies; do not re-enable startup writes to hide them.
- `CORS_ALLOWED_ORIGINS` contains comma-separated exact approved browser origins. Default is the existing Vercel project origin plus localhost/127.0.0.1 ports 5173/4173. Add specific verified Preview/custom origins; no wildcard hosting tenants. Bearer headers are supported; credential cookies are not used.
- Retain verified Cloudinary runtime settings. Review all existing datasource/logging/profile/environment-group overrides. SQL/bind/request/error defaults are safer, but external settings can override them.
- Never enable `local-admin-bootstrap` on a hosted service. It remains opt-in locally, requires explicit credentials and is excluded by production/prod profiles. The earlier fixed admin/default key and initializer repair SQL remain removed. Existing accounts/roles are untouched.

## Security changes verified locally

JWT signatures/expiry are verified before resolving an account; current DB USER/ADMIN authority governs access, not stale token role claims. Missing/invalid-role accounts do not authenticate, DB lookup failures fail closed and role demotion applies on the next request. Login rejects invalid roles without automatic role repair. Credential DTO validation and password log exclusion remain compatible with registration/login.

Admin/product/upload/status routes have explicit HTTP restrictions and method guards. Guest direct checkout no longer links accounts using submitted email; only an authenticated principal can supply ownership. Customer detail reads reject guest/other-owner orders. Existing historical associations are preserved for operator investigation.

COD remains PENDING_COD. Raw card/status/amount fields and unsupported online payments are rejected. Fulfilment status accepts only the current PENDING/PROCESSING/SHIPPED/DELIVERED/CANCELLED vocabulary and never changes stored payment state or monetary amounts. This does not define transition rules or verified collection.

Unknown products cannot create cart stubs; quantity/combined stock and trusted merchandise pricing are checked without invented fallbacks. Cart JSON omits User, public review JSON omits email, and frontend review labels do not derive from emails. Authentication/exception/SQL binding output and full frontend error-object logging are removed or restricted; validation responses do not disclose rejected values.

Fixed LKR 350 delivery, sale/coupon behavior and historical stored totals are unchanged. No schema column was added or production migration/data rewrite performed.

## Actual verification and remaining work

The **full current suite passes 134 tests, zero failures/errors/skips**, and `.\mvnw.cmd -o -B package` succeeds. All tests use mocks/security MVC or explicitly replaced H2 contexts. OrderControllerTest now uses an isolated MVC slice with actual authority names; no full application/hosted database is started. The frontend has 32 passing delivery/readiness tests and 67 intercepted browser checks.

Remaining technical work includes stock/cart concurrency, persistent idempotency, supported per-account/token revocation/reset/abuse controls, order response DTO minimization, current-price consent, auditable COD collection/status transitions and admin all-order reporting. Hosted runtime/schema/roles/CORS/logs/regions and deployed revision behavior remain unverified. Existing product records, credentials, old sessions and historical email-based order associations are not repaired by changing source.

Read the exact manual configuration/generation/rotation/exposure/rollback procedure in the shared frontend workspace's `docs/RENDER_SECURITY_RUNBOOK.md`, plus `docs/PRODUCTION_PHASE2_REPORT.md` and `docs/PAYHERE_BLOCKER_INVENTORY.md`. Do not print prior values from Git/diffs; review credential locations without exposing their contents. No Git history rewriting is authorized.

During the eventual authorized release, prevent old JWT-accepting instances from serving protected traffic, replace all with the new key, and require fresh logins. Password reset or clearing one browser does not revoke other copies of its JWT. Do not restore an exposed key on rollback. Keep ordering unavailable during incompatible backend/frontend transitions, verify the reviewed Render backend first and then the matching Vercel frontend. All genuine owner/technical sign-offs and zero-issue strict Production builds are still required.
