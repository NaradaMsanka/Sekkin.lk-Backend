# Isolated PostgreSQL release verification

Verified on 16 November 2026. The full backend package passed **209 tests, zero failures, errors or skips**, including **14 actual PostgreSQL integration cases**. The initial focused PostgreSQL run also passed all 14 cases. No hosted database, real customer account, production order or deployment was used.

Evidence: [full suite counts and artifact hashes](verification/postgres-release.json), [initial focused run](verification/postgres-focused-initial.json), [integration source](../src/test/java/com/cosmetic/ecommerce/postgres/PostgresReleaseIntegrationTest.java), and [synthetic pre-migration schema](../src/test/resources/postgres/legacy-schema.sql).

## What was exercised

- Official PostgreSQL **17.12**, image `postgres:17-alpine`, digest `sha256:a77aa70e641402a21411b92b721c58d10f23b50ae5fdd2333efd22d166e1e5f7`. PostgreSQL 17 is maintained through November 2029 according to the [official versioning policy](https://www.postgresql.org/support/versioning/).
- A newly created disposable database containing only synthetic legacy users/products/orders, followed by the exact reviewed additive SQL migration **twice**. Hibernate then started with **schema validation**, without `update`, `create` or `create-drop`.
- The required unique request-key index and unchanged historical LKR 0 delivery, line price and stored total. Historical reservation/retry/audit metadata remains unknown/null.
- Genuine PostgreSQL row locks under concurrent last-unit checkout, duplicate request-key conflicts and transaction rollback/recovery, cart checkout retries, guest ownership rejection, and reservation restoration exactly once.
- COD acceptance, delivery remaining unpaid, evidence/reference requirements, exact stored collection amounts and concurrent collection preserving one audit/operator.
- LKR 350 delivery, merchandise-only coupon deductions, unchanged historical values, actual JWT/login/database role checks, per-account/global-browser-session revocation, account disabling and explicit CORS including `Idempotency-Key`.
- The actual application security chain and controllers using MockMvc, backed by PostgreSQL: guest COD HTTP creation/retry and refusal of populated raw-card fields. MockMvc does not start Tomcat or prove live proxy/TLS behavior.

During contention PostgreSQL emitted a real `23505` duplicate-key violation. Recovery returned the existing order only after the failed creation transaction rolled back; it reserved stock once.

## Reproduce locally

Start the existing Docker daemon, use Java 21, and run from this backend checkout:

```powershell
.\scripts\verify-postgres.ps1
```

The checked-in runner uses the repository Maven wrapper (verified Maven 3.9.16), defaults to offline dependency resolution, caps Maven/test heaps, creates a fresh labelled PostgreSQL container and network, publishes PostgreSQL to **127.0.0.1 only**, runs the complete package and saves counts. Pass `-Online` only when dependency downloads are needed. It records the actual image digest and version on each run.

The local fixture deliberately uses trust authentication and no TLS. It contains no production credentials. The test refuses any URL outside the exact loopback/database pattern, requires a disposable database marker, and refuses an existing nonempty schema before executing fixture DDL. The runner removes only the generated resources bearing its ownership label, including its anonymous data volume, and restores task environment variables. Docker Desktop itself and downloaded image/tool caches are not production resources and are not silently uninstalled.

The final successful Maven invocation ran all 209 cases and packaged the JAR. Windows PowerShell wrapper issues encountered during preparation (native stderr handling, the image's temporary initialization server and dictionary summary conversion) were corrected. They were not application test failures. The evidence was populated from the completed JUnit reports; the original success counts were preserved. There is no reason to repeat application tests merely to reformat evidence.

The migration header was subsequently corrected to distinguish disposable rehearsal from production execution. This changes comments only: the full-suite evidence retains the SHA-256 of the bytes used in that run (`3fff63c24bd56b4d334eed5b229b35834546e065ab74a42c55262bb27bd17cef`). The separate container smoke records the updated comment-only file hash and rehearses it in another disposable PostgreSQL database. No SQL statement changed, and neither file was applied to production.

## What still requires the actual hosting accounts

This fixture models the application's old schema; it is not a copy or audit of Supabase's actual schema. Verify the deployed PostgreSQL version, extensions, role privileges, row/index definitions, JDBC endpoint/pooler settings and backup/restore procedure before applying the migration. `IF NOT EXISTS` cannot validate a pre-existing column or index definition.

Rehearse against the approved isolated provider environment, including the real connection/TLS/CA requirements. Verify certificate and hostname validation, Render's actual profile/environment overrides, schema validation, trusted proxy handling and the effective request source used by rate controls. The local loopback fixture proves none of those hosted facts.

Verify the actual Render revision/container, CORS origins, session revocation and permitted administrative operations in staging. Courier evidence in these tests is synthetic: authorized operators must check actual settlement reports before recording real COD collection. Credential exposure containment, signing-key rotation on all accepting instances and administrator review remain operator actions; unrelated business approvals must not delay them.
