# Maintained Spring Boot migration

Prepared 16 November 2026. Local source/build changes only. No deployment, hosted configuration, credential rotation or production database operation was performed.

The parent moved from Spring Boot **3.3.3 to 4.1.2**, verified against the [Spring project release page](https://spring.io/projects/spring-boot/) and the actual Maven Central parent/BOM. Java 21 remains the compilation target; it is supported by the [4.1.2 system requirements](https://docs.spring.io/spring-boot/system-requirements.html).

## Resolved platform

| Component | Resolved version |
| --- | --- |
| Spring Boot | 4.1.2 |
| Spring Framework | 7.0.10 |
| Spring Security | 7.1.2 |
| Embedded Tomcat | 11.0.27 |
| Hibernate ORM | 7.4.13.Final |
| PostgreSQL JDBC driver | 42.7.14 |
| Jackson application JSON | 3.1.7 |
| Jackson 2 compatibility runtime | 2.21.7 |
| JJWT API/implementation/Jackson adapter | 0.13.0 |
| H2 (tests only) | 2.4.240 |
| JUnit Jupiter | 6.0.3 |

The Spring Boot BOM manages the framework, servlet container, persistence, JSON and test versions. No individual older Spring, Security, Tomcat or Hibernate version override was introduced. JJWT is not managed by that BOM; its three artifacts share the explicit `jjwt.version` property. Version 0.13.0 was verified against the [official JJWT release](https://github.com/jwtk/jjwt/releases/tag/0.13.0) and Maven Central metadata.

## Compatibility changes

- Use the dedicated Web MVC starter and Web MVC, Security and Data JPA test starters.
- Move test-slice, entity scanning and `TestEntityManager` imports to the Boot 4 modules; replace removed `@MockBean` with Spring Framework `@MockitoBean`.
- Use Jackson 3 for application image-gallery parsing and application serialization tests. Jackson annotations retain their existing supported package.
- Update JWT parsing to the JJWT 0.13 verified-signature parser API with a `SecretKey`. Preserve literal UTF-8 signing material, minimum key length, HS256 signing, expiry and session-version validation.
- Retain BOM-managed Jackson 2 solely for JJWT's Jackson adapter. The [official Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) supports libraries that require Jackson 2 alongside application Jackson 3. This is not a fallback to an old unmaintained JSON version.
- Check packaged Boot 4.1.2 configuration metadata: existing error-detail suppression, multipart limits and Hibernate validation property names remain supported.

The fixed LKR 350 authoritative delivery rule, historical order totals, request receipts, COD-only payment restrictions, ownership, authorization, session revocation and audited COD collection contracts are preserved.

## Controlled ordering pause

`CHECKOUT_ENABLED=false` now prevents both creation flows in `OrderPlacementService`, before that service's retry lookup, account read or order/inventory write. Authentication filters may still read account data before reaching the service. Valid checkout requests receive HTTP 503 and `Retry-After: 60`. It also pauses idempotent receipt replays through those POST routes. Existing order reads, product reads and administrator operations remain available through their existing authorization boundaries.

The default is true to preserve existing local behavior. Operators must set false explicitly during a coordinated release and verify it on the reviewed backend revision before relying on it. Older deployed binaries do not implement this switch. Changing a saved Render variable does not immediately change a running deployment; use the coordinated operator procedure. The property does not itself supply an ordering pause for an old incompatible backend.

## Verification boundaries

The migrated platform passed the original **192 backend tests and packaging**, with zero failures, errors or skips. Three additional tests cover the ordering pause, response header, absence of repository writes and continued order reads; the consolidated final result is recorded by the release report and Maven/Surefire evidence.

Tests include real isolated H2 transaction/concurrency checks, synthetic JWT and current-database-role security boundaries, privacy serialization, fixed/stored monetary totals and COD audit controls. Their success does not prove hosted PostgreSQL, Render proxy/TLS/runtime configuration, production credential containment or external account regions. Real isolated PostgreSQL verification and current dependency assessment are recorded separately; no uncompleted verification is approved here.

Dependencies were fetched using temporary task-specific Maven proxy settings derived from the existing OS proxy. No global Maven settings were changed, no proxy credentials were printed and the temporary settings were not added to the repository. Subsequent offline packaging uses the populated cache.
