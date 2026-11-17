# SEKKIN.LK demo and submission guide

Reviewed: 17 November 2026. Target: academic/project demonstration with cash-on-delivery orders.

## Project update

The main application is implemented. The work now is verification and submission packaging, rather than another large feature phase.

| Area | Current implementation |
| --- | --- |
| Storefront | Responsive catalog, search/filter controls, quick view, product details and image galleries |
| Accounts | Registration/login, BCrypt passwords, JWTs, customer/admin roles and session revocation |
| Cart | Add/update/remove items, stock checks, quantity feedback and totals |
| Checkout | Cart and direct COD requests, coupons, one LKR 350 delivery charge and retry protection |
| Orders | Customer history, admin acceptance, fulfilment status, cancellation and separate cash collection |
| Admin | Dashboard, product/stock operations, image editor/sequence management and order operations |
| Reviews | Public reviews and purchase/delivery eligibility checks |
| Business pages | Six routes with visible draft notices; final business decisions remain incomplete |
| Stack | Java 21 / Spring Boot / PostgreSQL; React 19 / Material UI / Vite / Node.js 22 |

The new local demo uses H2 in PostgreSQL compatibility mode so the presenter does not depend on the hosted API or an installed database. Normal PostgreSQL configuration remains separate. H2 is a demonstration convenience, not evidence of production database compatibility.

## Start the demo

Keep two terminals open. The first dependency download requires internet access; install dependencies before the presentation.

Terminal 1, from the backend repository:

```powershell
.\start-demo.ps1
```

Wait for Spring Boot to finish starting. The terminal prints an administrator email and a newly generated temporary password. The API is `http://localhost:8080`.

Terminal 2, from the frontend repository (`Frontend/frontend` in the shared workspace):

```powershell
npm ci
npm run dev:demo
```

Open `http://localhost:5173`. This Vite mode explicitly selects the local API, including when a pre-existing `.env` points to Render. Ports 8080 and 5173 must be free.

The demo binds to loopback, uses an isolated in-memory database, creates three sample products and enables COD checkout. A startup guard rejects an external/persistent database or mixed `demo` and production profiles before Hibernate starts. Normal authentication and authorization still apply. The H2 web console is disabled.

Stop both terminals with Ctrl+C. Restarting the backend resets all demo users, inventory, orders and the admin password. Do not use real customer information or treat the simulated fulfilment/collection actions as real business records.

## Presentation walkthrough

1. Explain the customer/admin roles and React -> Spring Boot -> database architecture. State that the presentation uses a temporary local database and COD.
2. Show the home page, product details, searching/filtering and mobile layout. The seeded products are explicitly labelled as demo examples and have image fallbacks; use your own approved product imagery if your submission requires it.
3. Sign in with the printed administrator account at `/login`; open `/admin`. Demonstrate product details, stock changes and dashboard data.
4. Use a separate private browser window to register a customer. Add a product to the cart and change its quantity.
5. Open checkout, enter synthetic delivery details, show the subtotal plus LKR 350 delivery, optionally apply `WELCOME10`, and submit a COD request.
6. Show the customer's order history. Explain that a request acknowledgement is not acceptance or payment.
7. In the admin window, accept the pending order with a demo reference, then mark it shipped and delivered. Demonstrate the separate COD collection action using a demo reference and exact total. No payment provider is contacted.
8. Refresh customer order history and the dashboard. If useful, show review eligibility for the delivered purchase and a cancellation/restock case with another sample order.
9. End with the current test results and known limitations below.

## Checks

Backend, from its repository:

```powershell
.\mvnw.cmd -B test
.\mvnw.cmd -B package
```

Frontend, from its repository:

```powershell
npm run lint
npm test
npm run build
```

Verification results for this review are recorded in [the current verification record](SUBMISSION_VERIFICATION.md). Older `ui-review` screenshots and reports used isolated browser fixtures; they should not be presented as a fresh live checkout test.

## Submission package

- [ ] Include the backend and frontend source, each README, dependency manifests/lockfile, Maven wrapper files and safe `.env.example` templates.
- [ ] Include this guide and the verification record. Preserve the separate PostgreSQL migration and CI configuration.
- [ ] Prepare your required title page, abstract/problem statement, objectives, architecture, ER diagram, feature screenshots and test table according to your course rubric.
- [ ] Record a short demonstration using the walkthrough; capture screenshots after you have selected the final demo products/images.
- [ ] Confirm the assessment's required deliverables, naming convention and submission format.
- [ ] Review the final changes in both Git repositories and save a submission commit/archive. The workspace root itself is not a Git repository.
- [ ] Exclude private environment files, secrets, uploads containing real customer data, `node_modules`, `target`, IDE caches and crash logs.

The two repositories already contained substantial uncommitted work before this review. Do not discard it while creating the submission package.

## Known limitations and future work

- PayHere, CARD and KOKO payments are not integrated. Automated email confirmations and courier integration are not implemented/verified. The supported demonstration is COD.
- Business details, support-contact confirmation and policy wording still need owner decisions. The merchant checker currently reports 75 unresolved items. These are not 75 application defects and are not required to claim a local academic demo; they prevent claiming a finished commercial store.
- Cloudinary upload/edit-save requires configured backend credentials. The local demo intentionally leaves them empty; do not present image upload as verified in this mode.
- The frontend build reports a large JavaScript bundle warning. It builds successfully; performance work remains available after the demo.
- Older admin/image-editor components emit Material UI compatibility warnings. The browser walkthrough completed, but updating those legacy props remains UI polish.
- The PostgreSQL-specific suite requires a deliberately empty disposable database and is skipped without one. Production migrations, backup/restore and hosted checkout have not been established by the local demo.
- A read-only check on 17 November 2026 returned HTTP 200 for the hosted frontend; hosted product/category API requests each timed out after 20 seconds. This does not prove a permanent outage. Use the local demonstration until hosted availability is separately verified.
- Earlier merchant/readiness reports are historical task records. Some describe findings subsequently changed in the current working tree; use the current source and verification record for this submission.
