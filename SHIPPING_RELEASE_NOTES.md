# Fixed LKR 350 order delivery

**Current final preparation:** normal shipping remains **LKR 350**, coupons reduce merchandise only, and historical saved amounts are preserved. Full packaging passed **209 tests**, including **14 real PostgreSQL** cases, plus **15 actual container HTTP/PostgreSQL checks**. Current readiness is **10**; [the final report](../Frontend/frontend/docs/FINAL_PRODUCTION_COMPLETION_REPORT.md) contains completed local evidence and remaining operator requirements. No production order/stock/fee was changed. **The 36/192/77 snapshot and shipping-only limitations below are historical.**

Current owner-approval phase: full backend suite **192/192** and package pass; frontend **43** tests and **77** browser checks pass. Readiness is **36**. Fixed LKR 350 delivery and stored historical amounts are preserved. See SECURITY_RELEASE_NOTES.md and the frontend OWNER_APPROVAL_PHASE_REPORT.md for new order/session controls, required schema migration and outstanding dependency/hosted verification. Shipping-only statements below describe that historical phase; the new session/order fields require a separately reviewed migration.

Prepared locally on 16 November 2026. Nothing has been pushed, merged, deployed or changed in the production database.

`OrderPricing.DELIVERY_CHARGE` is the single backend constant: `new BigDecimal("350.00")`. Both normal and direct COD orders use trusted catalog effective prices, existing merchandise coupons and one delivery charge. Coupons cannot reduce delivery. Every new order explicitly saves its fee and total; old orders retain saved amounts. No shipping schema migration is needed. The existing Double entity/API fields are retained; arithmetic uses two-decimal BigDecimal/HALF_UP before conversion.

Cart prices/totals are not trusted. Empty/invalid orders and invalid or combined excessive quantities are rejected before inventory writes. Direct DTOs reject unsupported client price/fee/total fields; cart body extras cannot override the calculation. Phase 2 fixes cart mutation/stubs and ownership. Concurrency and persistent idempotency remain unresolved.

History responses add the saved delivery field. Direct responses include the merchandise coupon reduction derived from saved line prices, delivery and total. Existing response fields remain available. Do not alter existing order amounts or automatically backfill old zero fees.

From the Backend repository, the existing wrapper runs the selected isolated tests and packages the application:

```powershell
.\mvnw.cmd -o -B '-Dtest=OrderShippingTest,OrderShippingPersistenceTest,DirectOrderPaymentSafetyTest,OrderServiceTest,ProductServiceTest,ReviewServiceTest,ImageUploadSecurityTest,ReviewSecurityTest,CloudinaryServiceTest,ProductImageServiceTest,JwtConfigurationSafetyTest,UserSerializationSafetyTest,AdminBootstrapSafetyTest' package
```

Result: **87 tests, zero failures/errors/skips, package success**. `OrderShippingPersistenceTest` uses a replaced embedded H2 datasource with the H2 dialect; it saves both new order types and verifies an existing zero-fee order is unchanged. Phase 2 converts `OrderControllerTest` to an isolated MVC slice and includes it in the passing full suite. No hosted PostgreSQL test or real customer transaction was performed.

Review these shipping files:

- `src/main/java/com/cosmetic/ecommerce/service/OrderPricing.java`
- `src/main/java/com/cosmetic/ecommerce/service/OrderService.java`
- `src/main/java/com/cosmetic/ecommerce/controller/OrderController.java`
- `src/main/java/com/cosmetic/ecommerce/dto/OrderResponseDTO.java`
- `src/main/java/com/cosmetic/ecommerce/dto/DirectOrderResponseDTO.java`
- `src/test/java/com/cosmetic/ecommerce/service/OrderServiceTest.java`
- `src/test/java/com/cosmetic/ecommerce/service/DirectOrderPaymentSafetyTest.java`
- `src/test/java/com/cosmetic/ecommerce/service/OrderShippingTest.java`
- `src/test/java/com/cosmetic/ecommerce/service/OrderShippingPersistenceTest.java`

The working tree also contains earlier security patches. Read [SECURITY_RELEASE_NOTES.md](SECURITY_RELEASE_NOTES.md) before any Render release: `JWT_SECRET` is now mandatory, old sessions/signing material require coordinated rotation, existing default-admin credentials require separate remediation, and datasource runtime variables are mandatory, and schema validation/role behavior still needs staged review. A shipping migration is unnecessary; this does not approve unrelated schema behavior.

For an eventual authorized rollout, prevent incompatible checkout clients from accepting orders during the backend/frontend transition. Release the reviewed Render revision first and the matching Vercel frontend next; verify both before reopening orders. Old UI/new API can display zero and charge 350; new UI/old API can display 350 and save zero. No automatic maintenance control was added. Roll back compatible versions together with ordering unavailable, preserving all recorded totals.

The frontend still has **61 merchant-readiness blockers**. Do not bypass its Production gate. Review the matching frontend report at `Frontend/frontend/docs/FIXED_DELIVERY_IMPLEMENTATION.md` in the shared workspace. Local passing tests are not a clean payment-safety sign-off or permission to enable PayHere/card processing.
