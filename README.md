# SIT-BE — AISA API

Backend for the **AIML Student Association** site, Department of CSE (AI & ML), Dr. Bapuji
Salunkhe Institute of Engineering & Technology, Kolhapur.

Spring Boot 3.5 · Java 25 · **Cloud Firestore** · JWT · Cloudinary

> **Frontend:** [Yaseenyk/SIT-FE](https://github.com/Yaseenyk/SIT-FE) — a Next.js static
> export on GitHub Pages. It talks to this API over HTTPS.

---

## Quick start

No database to install — Firestore is managed. Develop against the **emulator**, so
nothing touches the real project:

```bash
cp .env.example .env      # set JWT_SECRET (openssl rand -base64 48)
                          # and ADMIN_BOOTSTRAP_PASSWORD

# terminal 1 — the emulator
npx firebase-tools emulators:start --only firestore --project aisa-local

# terminal 2 — the API
FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 mvn spring-boot:run
```

To run against the real project instead, set `FIREBASE_PROJECT_ID` and
`FIREBASE_SERVICE_ACCOUNT` in `.env` and leave `FIRESTORE_EMULATOR_HOST` unset.

- API docs (Swagger UI): <http://localhost:8080/docs>
- Health: <http://localhost:8080/actuator/health>

On first boot against an **empty** project the seeder writes the association's committees,
members, events and achievements. It is guarded per collection, so it never overwrites and never runs twice.

> **If the project already holds documents written by the ORIGINAL single-file site, they
> are not readable as-is.** The collection *names* match but the field names do not
> (`cat` vs `category`, `desc` vs `description`, `date` vs `startsOn`, and members
> referenced their committee by *name*). The seeder also skips a non-empty collection, so
> you would get neither the old content nor the new. Point this at empty collections, or
> convert the old documents first.

Sign in to the frontend's `/admin/` with the bootstrap credentials, then change the password.

---

## What it replaces

The original site was a single 4,019-line HTML file that called **Firebase** (Firestore,
Auth, Storage) directly from the browser, with the config key committed in the markup.

| | Before | Now |
| --- | --- | --- |
| Authorisation | Firestore rules; any authenticated client could write | One rule set in `SecurityConfig`, covered by an integration test |
| Login hardening | Browser-side captcha and attempt counter — both reset on reload | BCrypt + per-account lockout in the database |
| Events | `'Oct 10–12, 2024'` strings re-parsed on every page load | Real dates + a derived `lastDay` field; upcoming/past computed server-side |
| Members | Committee matched by display **name** — renaming orphaned them | Referenced by immutable id; delete unassigns them explicitly |
| Images | Firebase Storage: any size, any path | Cloudinary signed direct upload, resize enforced server-side |

`docs/architecture.md` has the full account.

---

## The security model

**Public reads. Admin writes. Enforced here, nowhere else.**

Public GET endpoints are *enumerated* in `SecurityConfig`; everything else requires
`ROLE_ADMIN`. That direction matters — an earlier blanket `GET /api/v1/**` permit rule
exposed `/settings/admin`, publishing the staff notification address to anonymous callers
with nothing to reveal it. Listing the public paths means a new admin endpoint is private
by default.

`SecurityRulesIntegrationTest` is the test that must never be weakened. **Adding an
endpoint means adding its case there.**

Tokens are 12-hour HS256 JWTs. There is no `/logout` — the token is stateless, so signing
out is discarding it. **Rotating `JWT_SECRET` revokes every issued token at once**; that
is the revocation mechanism.

---

## Configuration

Everything comes from the environment (and `.env` locally, via spring-dotenv). Nothing
sensitive is committed. See `.env.example` for the annotated list.

| Variable | Notes |
| -------- | ----- |
| `FIREBASE_PROJECT_ID` | The Firebase project holding the data |
| `FIREBASE_SERVICE_ACCOUNT` | The service-account JSON, raw or base64. **A private key** |
| `FIRESTORE_EMULATOR_HOST` | Set locally to use the emulator; ignores the two above |
| `JWT_SECRET` | Required — the app refuses to start without it. `openssl rand -base64 48` |
| `CORS_ALLOWED_ORIGINS` | The SIT-FE origin, exactly. No trailing slash, no path |
| `CLOUDINARY_*` | Image uploads. Unset is fine: everything else works, uploads return 503 |
| `ADMIN_BOOTSTRAP_PASSWORD` | Creates the first admin on boot **if none exists**. Set once, then clear it |

---

## Layout

```
src/main/java/org/aisa/api/
├── config/      security rules, CORS, typed properties, clock
├── common/      BaseEntity, exceptions, one error shape for the whole API
├── security/    JWT issue + verify, the auth filter
├── auth/        admin account, login, credential changes, first-admin bootstrap
├── committee/ member/ event/ gallery/ achievement/ message/ settings/
│               one package per domain: entity, repository, DTOs, service, controller
├── media/       Cloudinary signed uploads
└── stats/       the counters the home page and dashboard read

└── firestore/    the Firestore client, document mapping, collection names, the seeder
```

There is no schema and no migrations. Firestore accepts any shape, which moves the burden
onto the mapping layer: `firestore/Documents.java` converts every field by hand, because
the SDK's reflective mapper silently reads `null` for a field that was renamed — and null
is legitimate for most fields here, so nothing would surface until a page rendered blank.

---

## Checks

```bash
# start the emulator first (see Quick start), then:
FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 mvn verify
```

`ReferentialIntegrityTest` is the one to keep green: Firestore enforces no integrity, so the
guarantees a foreign key used to give are now ordinary code that can be deleted by accident.

---

## Deploying

`render.yaml` is a Render blueprint describing the service and its database:
**New → Blueprint** → point at this repo.

Render runs **only the API** — Firestore is hosted by Google, so there is no database
service to provision and nothing that expires. Set `FIREBASE_PROJECT_ID` and
`FIREBASE_SERVICE_ACCOUNT` (base64) in the dashboard. Full walkthrough in
`docs/deployment.md`.

---

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — binding conventions for changes to this repo
- [`docs/architecture.md`](docs/architecture.md) — the whole system, data model, security model
- [`docs/deployment.md`](docs/deployment.md) — Render + Cloudinary, every variable, troubleshooting
- `/docs` on the running API — Swagger UI
