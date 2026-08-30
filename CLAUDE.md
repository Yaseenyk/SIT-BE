# CLAUDE.md — SIT-BE

AI guardrails for this repository. Binding for every code generation, refactor and review.
When a rule here conflicts with a general habit, this file wins.

---

## 0. What this is

The API behind **AISA**, the AIML Student Association site at BSIET Kolhapur.
Spring Boot 3.5 · Java 25 · PostgreSQL · Flyway · JWT · Cloudinary.

The frontend is a separate repository:
[Yaseenyk/SIT-FE](https://github.com/Yaseenyk/SIT-FE) — a static export on GitHub Pages,
which is why this service must be reachable cross-origin and must own every write.

Read `docs/architecture.md` before changing anything structural.

---

## 1. The security model (hard rule)

**Public reads. Admin writes. Enforced here, nowhere else.**

- Authorisation lives in `config/SecurityConfig.java` as *rules*, not as annotations
  sprinkled across controllers. A rule you cannot see in that one file does not exist.
  Do not add `@PreAuthorize` to individual handlers as a substitute.
- **Public GETs are enumerated; everything else requires `ROLE_ADMIN`.** Never invert this
  to a blanket `GET /api/v1/**` permit with carve-outs. That is what the first version did,
  and `/settings/admin` slipped through it — publishing the staff notification address to
  anonymous callers, with nothing anywhere to reveal it.
- `SecurityRulesIntegrationTest` **must never be weakened**. Adding an endpoint means
  adding its case there. A write endpoint reachable anonymously is the worst bug this
  project can ship, and it throws no exception.
- The frontend is **not** a security boundary. Never accept a check moved there.

### Secrets

- Every secret comes from the environment (`.env` locally). Nowhere else.
  `.env.example` documents each one and is the file you update when adding one.
- The app **refuses to start without `JWT_SECRET`**, by design. Never add a default.
- The original HTML this replaced had a live Firebase key committed in it. Do not
  reintroduce that pattern for any provider.

---

## 2. Flyway owns the schema

- `spring.jpa.hibernate.ddl-auto` is `validate` in every profile. **Never** set it to
  `update` — not "just locally". Hibernate silently altering a table is how a schema drifts
  out of sync with its migrations.
- Schema changes are a **new** `V<n>__description.sql`. Never edit an applied migration;
  Flyway checksums them and the next deploy fails.
- A migration may run against a database an admin has already edited. Prefer
  `INSERT … ON CONFLICT DO NOTHING` and targeted `UPDATE`s; **never `DELETE`** their work.

---

## 3. Layering

`Controller` → `Service` → `Repository`.

- Controllers do routing, `@Valid`, and status codes. No business logic.
- Services own transactions and never import a web type. Signal HTTP outcomes with the
  exceptions in `common/` — `NotFoundException`, `ConflictException`,
  `RateLimitedException`, `ServiceUnavailableException`. Throwing Spring's
  `ResponseStatusException` from a service both leaks a web concern into the service layer
  and gets swallowed by the catch-all handler, turning a deliberate 503 into a 500.
- **Never serialise an entity.** Controllers return DTOs. Returning an entity leaks
  admin-only columns — Cloudinary public ids, the notification email — and couples the JSON
  shape to column names.

### Errors

One shape, from `GlobalExceptionHandler`. Never build an error body in a controller, and
never let a stack trace reach the client.

### Validation at the boundary

Bean Validation on the request record. Rules a constraint cannot express (an end date
before a start date) are checked in the service with a readable message **and** backed by a
database constraint. Not in between.

---

## 4. Images

Uploads go **browser → Cloudinary**, signed by `POST /api/v1/media/signature`. The bytes
never pass through this service — a free-tier container has neither the memory to buffer
them nor a disk that survives a redeploy.

- The API secret never reaches the browser.
- The resize is inside the *signed* transformation, so a client cannot skip it.
- Deleting a record deletes its image; replacing one releases the old asset. **A new image
  field means wiring both paths**, or orphaned assets accumulate where nobody will find them.
- `deleteQuietly` logs rather than throws, on purpose: a failed remote cleanup must not
  fail the admin's delete.

---

## 5. Time

Inject the `Clock` bean; never call `LocalDate.now()` inline. It is fixed to
`Asia/Kolkata` because the container runs in UTC and "is this event still upcoming?" must
flip at midnight in Kolhapur. Injecting it is also what lets tests pin a date instead of
depending on when the suite runs.

---

## 6. Working agreement

- **Surgical changes only.** No drive-by refactors, no speculative abstractions.
- **Comments explain *why*, never *what*.** Every comment here records a decision, a
  constraint, or the bug the obvious alternative would cause — several record what the
  original single-file site got wrong. Match that bar.
- **No speculative error handling.** Validate at real boundaries — request bodies,
  Cloudinary. Not in between.
- **Verify before claiming done.** If you cannot run something, say so plainly.

---

## 7. Definition of done

- [ ] `mvn verify` passes (Testcontainers needs Docker running)
- [ ] Schema changes are a new migration, never an edit to an applied one
- [ ] New endpoints have a case in `SecurityRulesIntegrationTest`
- [ ] DTOs in, DTOs out — no entity crosses the controller boundary
- [ ] New config is in `.env.example` **and** `docs/deployment.md`
- [ ] Affected `docs/` updated in the same change
- [ ] No secret committed, and no default added for `JWT_SECRET`
