# Temporary public catalog containment

The owner authorizes the existing Render service and Vercel site to release a
non-transactional public catalog while hosted COD requirements remain incomplete.

`src/main/resources/public-release.properties` explicitly selects `PUBLIC_CATALOG`.
The main method starts the JDK maintenance HTTP server before Spring. It does not
initialize the datasource, application beans, JWT handling, customer accounts or
order processing. Every GET/HEAD returns only the same non-personal maintenance
document; every mutation method returns 503. Old Bearer tokens are ignored.
The standard Render `PORT` and nonsecret `RENDER_GIT_COMMIT` identify the process.

Local verification: 17 HTTP tests passed, packaging succeeded, and the actual
executable JAR started with no JWT/datasource configuration. Four mutation paths
returned 503. See `verification/catalog-packaged-smoke.json`. Earlier 209-test
transactional verification is retained as earlier evidence, not reported as a
new full run. Hosted containment must be independently verified after deployment.

Deploying this mode does not run a production schema migration, modify existing
orders, rotate credentials or make the preserved COD implementation ready.
Previously exposed signing material and administrator/history issues still need
operator containment before transactional activation. Do not restore an older
insecure binary as rollback; keep this maintenance revision running instead.

Reactivating COD requires an explicitly reviewed change to `COD_STORE`, the
approved migration and signing-key rotation, verified hosted configuration and
privacy/retention evidence, and passing full transactional gates. Changing an
environment variable cannot silently enable the database-backed application.
