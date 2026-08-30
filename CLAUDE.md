# CLAUDE.md — SIT-BE

AI guardrails for this repository. Binding for every code generation, refactor and review.
When a rule here conflicts with a general habit, this file wins.

---

## 0. What this is

The API behind **AISA**, the AIML Student Association site at BSIET Kolhapur.
Spring Boot 3.5 · Java 25 · Cloud Firestore · JWT · Cloudinary.

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

## 2. Firestore has no schema, so the code is the schema

There are no migrations and nothing validates a document's shape. That moves work onto
you, permanently:

- **Mapping is written by hand** in each repository's `toX`/`toMap`, plus
  `firestore/Documents.java`. Do NOT switch to the SDK's reflective POJO mapper: it reads
  `null` for a renamed or retyped field instead of failing, and `null` is a legitimate
  value for most fields here — so the breakage would first appear as a blank page, not an
  exception.
- **Collection names live in `firestore/Collections.java`.** A typo does not fail;
  Firestore creates the collection, and the data silently splits in two.
- **Dates are ISO strings** (`"2026-09-11"`), because lexicographic order equals
  chronological order, so range queries work with no extra machinery.
- **The seeder is guarded per collection** and must stay idempotent. It may run against a
  project an admin has already edited; it must never overwrite or delete their work.

### Derived fields are written, never scheduled

`event.lastDay` (= `endsOn ?? startsOn`) exists because Firestore cannot compare two
fields, which is how upcoming/past used to be expressed. It is recomputed **on every
save**, in `EventRepository.toMap`, so it cannot drift from the fields it derives from.

**Never replace it with a scheduled job.** That is precisely the original site's
`autoSortEvents()` bug — a value correct only as often as something remembers to update it.

---

## 2b. Referential integrity is now your job (hard rule)

Firestore enforces none. Two rules carry what a foreign key used to:

- **Reference committees by their immutable slug id, never by name.** The original site
  matched on display name, so renaming a committee orphaned every member on it.
- **`CommitteeService.delete` unassigns members FIRST, then deletes the committee.** That
  ordering is the guarantee: the two writes are not atomic with each other, and failing
  after the unassign leaves consistent data whereas failing after the delete leaves
  members pointing at a document that is gone.

`ReferentialIntegrityTest` pins all of it. **Never weaken it.** The failure it guards
against throws nothing and shows nothing — a member simply vanishes from the structure
page while still counting towards the member total.

### Ordering and grouping happen in memory

`nulls last`, `group by`, and `max(displayOrder)` have no Firestore equivalent, and
`orderBy` on a field **omits documents that lack it entirely** — an achievement saved with
no date would disappear rather than sort last. These collections hold tens of documents;
sorting them in Java is correct, index-free, and cannot lose a record. Do not "optimise"
them into Firestore queries without re-reading this.

---

## 3. Layering

`Controller` → `Service` → `Repository`.

- Controllers do routing, `@Valid`, and status codes. No business logic.
- Services never import a web type. Signal HTTP outcomes with the
  exceptions in `common/` — `NotFoundException`, `ConflictException`,
  `RateLimitedException`, `ServiceUnavailableException`. Throwing Spring's
  `ResponseStatusException` from a service both leaks a web concern into the service layer
  and gets swallowed by the catch-all handler, turning a deliberate 503 into a 500.
- **Never serialise an entity.** Controllers return DTOs. Returning an entity leaks
  admin-only columns — Cloudinary public ids, the notification email — and couples the JSON
  shape to column names.

### Atomicity

There are no Spring transactions — `@Transactional` does nothing against Firestore and is
gone. What remains:

- A single document write is atomic on its own.
- Multi-document operations use a `WriteBatch` (album create/delete, committee reorder,
  member unassign).
- **Operations spanning collections or an external service are NOT atomic.** Deleting a
  committee touches members, the committee, and Cloudinary; ordering is what makes the
  failure modes survivable. Say so in a comment wherever it matters.

### Errors

One shape, from `GlobalExceptionHandler`. Never build an error body in a controller, and
never let a stack trace reach the client.

### Validation at the boundary

Bean Validation on the request record. Rules it cannot express (an end date before a start
date) are checked in the service with a readable message.

There is **no database constraint behind any of it any more** — Firestore has no CHECK, no
NOT NULL, no UNIQUE. The service layer is the only thing standing between a bad payload and
a stored document, so a validation gap here is a data-corruption bug, not a UX one.

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

- [ ] `mvn verify` passes (needs the Firestore emulator running)
- [ ] New/renamed document fields are handled in the repository mapper AND the seeder
- [ ] Anything derived is written on save, never scheduled
- [ ] New endpoints have a case in `SecurityRulesIntegrationTest`
- [ ] DTOs in, DTOs out — no entity crosses the controller boundary
- [ ] New config is in `.env.example` **and** `docs/deployment.md`
- [ ] Affected `docs/` updated in the same change
- [ ] No secret committed, and no default added for `JWT_SECRET`
