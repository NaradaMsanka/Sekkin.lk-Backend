# Submission verification record

Date: 17 November 2026. Scope: current local working tree and the explicitly selected temporary demo. No changes were pushed or deployed and no production orders were placed.

## Automated checks

| Check | Result |
| --- | --- |
| Backend `mvnw.cmd -B test` | PASS: 213 discovered, 201 passed, 12 PostgreSQL-specific tests skipped, 0 failures/errors |
| Backend normal `mvnw.cmd -B -DskipTests package` | PASS; packaging verified separately from the test run |
| Backend `mvnw.cmd -B -Pdemo -DskipTests package` | PASS; embedded H2 runtime package builds |
| Frontend `npm run lint` | PASS: no remaining errors or warnings |
| Frontend `npm test` | PASS: all 37 delivery, order-request and readiness tests |
| Frontend `npm run build` | PASS; existing JavaScript bundle-size warning remains |
| Docker Compose configuration | PASS with an explicit test signing key; Docker engine was unavailable, so containers were not run |
| PowerShell demo launcher | Syntax checked and actually started successfully |
| Demo environment guard | Five tests cover isolated startup, mixed production profiles, external/persistent databases, driver overrides and loopback binding |

The PostgreSQL suite was not enabled. These results do not establish that a PostgreSQL migration or hosted checkout succeeds.

## Actual local API walkthrough

The launcher started the backend on loopback with a new in-memory H2 database, random JWT signing secret, random local administrator password and three synthetic products. A verification harness used real API requests and synthetic customer details to check:

1. Sample catalog availability, administrator login and customer registration/login.
2. Rejection of anonymous and customer access to administrator orders.
3. Cart add/update, server totals and absence of password fields in cart JSON.
4. `WELCOME10` checkout: LKR 1000 merchandise - LKR 100 coupon + LKR 350 delivery = LKR 1250.
5. A repeated retry key returns the existing order and reduces stock only once; successful checkout clears the cart.
6. Order visibility in the customer's authenticated history.
7. Administrator acceptance, shipping, delivery and separate simulated COD collection; collection updates revenue reporting.
8. Delivered-purchase review eligibility, review submission and omission of customer email from public reviews.
9. Guest direct COD checkout and cancellation with restored stock.

All checks passed. Payment collection was a synthetic local state transition; no gateway, courier or bank was contacted.

## Actual browser walkthrough

Headless Chrome used the running local frontend and backend. No API response fixtures were supplied. External network requests were blocked; none were attempted.

- Customer registration and automatic sign-in completed through the UI.
- Home rendered at 1440px and 375px; the mobile home page had no horizontal overflow.
- Product -> add to cart -> checkout -> coupon total -> order acknowledgement -> customer history succeeded.
- Order history refresh succeeded after the state-loading change.
- Administrator login, order reporting and the explicit acceptance dialog saved against the local API.
- Image editor loaded a local image and reopened cleanly. Cloudinary upload/save was not attempted.
- No uncaught browser runtime errors were observed.

The browser still emitted existing Material UI compatibility warnings for legacy admin/image-editor props (`item`, layout props, `inputProps` and `PaperProps`). The verified actions completed; a broader Material UI API cleanup remains UI polish. These warnings are distinct from uncaught runtime errors and ESLint results.

Fresh JSON reports, harnesses and screenshots are in the workspace's `ui-review/submission` directory. Screenshots cover desktop/mobile home, desktop checkout/history, admin orders and the image editor. This directory is outside both Git repository roots; include it separately if needed for the submission.

## Changes made during this review

- Added an explicit temporary local demo with sample catalog, generated administrator credentials and a guard that runs before database startup.
- Added `npm run dev:demo` to select the local API and `npm test` to run all existing frontend tests.
- Fixed the 16 frontend lint errors and one warning: removed unused imports, reset image-editor state through fresh mounts, and made order-history reads cancellable with a separate refresh trigger.
- Restored the safe backend `.env.example` to trackable files, completed its variable list, and passed the required JWT secret through local Compose configuration.
- Replaced stale/template READMEs, removed a broken deployment-report link, and added the current status, demo walkthrough and submission checklist.
- Preserved the substantial pre-existing uncommitted application work in both repositories.

## Outstanding scope

For submission: choose the final presentation images/catalog, prepare the assessment-specific written report and recording, and save the reviewed source package/commits. The executable core demo has been verified as above.

For a commercial launch: finish business policies/contact confirmation, review production migrations and hosting, verify production data/availability and configure any real external services. `npm run check:merchant` currently refuses merchant readiness with 75 unresolved items. CARD, KOKO, PayHere, automated emails and courier integration remain outside this demo.
