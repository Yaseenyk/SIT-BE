# SIT-BE — AISA API

Backend for the **AIML Student Association** site, Department of CSE (AI & ML), Dr. Bapuji
Salunkhe Institute of Engineering & Technology, Kolhapur.

Spring Boot 3.5 · Java 25 · PostgreSQL · Flyway · JWT · Cloudinary

> **Frontend:** [Yaseenyk/SIT-FE](https://github.com/Yaseenyk/SIT-FE) — a Next.js static
> export on GitHub Pages. It talks to this API over HTTPS.

---

## Quick start

Docker required for the database.

```bash
cp .env.example .env      # set JWT_SECRET (openssl rand -base64 48)
                          # and ADMIN_BOOTSTRAP_PASSWORD
docker compose up         # API on :8080
```

- API docs (Swagger UI): <http://localhost:8080/docs>
- Health: <http://localhost:8080/actuator/health>

Flyway creates the schema and seeds the association's committees, members, events and
sample content on first boot. Sign in to the frontend's `/admin/` with the bootstrap
credentials, then change the password.

Running the app from an IDE instead? `docker compose up -d db` gives you just Postgres.

---

## What it replaces

The original site was a single 4,019-line HTML file that called **Firebase** (Firestore,
Auth, Storage) directly from the browser, with the config key committed in the markup.

| | Before | Now |
| --- | --- | --- |
| Authorisation | Firestore rules; any authenticated client could write | One rule set in `SecurityConfig`, covered by an integration test |
| Login hardening | Browser-side captcha and attempt counter — both reset on reload | BCrypt + per-account lockout in the database |
| Events | `'Oct 10–12, 2024'` strings re-parsed on every page load | Real dates; upcoming/past derived server-side |
| Members | Committee matched by display name — renaming orphaned them | Foreign key, `ON DELETE SET NULL` |
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
| `DATABASE_URL` | **JDBC form**: `jdbc:postgresql://host:5432/db`. Render's `postgres://…` string is not accepted |
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

src/main/resources/db/migration/   Flyway — the only thing allowed to change the schema
```

`ddl-auto` is `validate` everywhere. Schema changes are a **new** `V<n>__*.sql`; never edit
an applied migration, because Flyway checksums them.

---

## Checks

```bash
mvn verify        # needs Docker running — Testcontainers starts a real Postgres
```

---

## Deploying

`render.yaml` is a Render blueprint describing the service and its database:
**New → Blueprint** → point at this repo.

One manual step: Render hands you a `postgres://…` connection string, which JDBC does not
accept. Convert it to `jdbc:postgresql://host:5432/db` and set the username and password
separately. Full walkthrough and troubleshooting in `docs/deployment.md`.

> Render's free Postgres **expires after 30 days**. For a site that must survive a
> semester, move to a paid tier or point `DATABASE_URL` at Supabase or Neon — it is plain
> PostgreSQL, so nothing in the code changes.

---

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — binding conventions for changes to this repo
- [`docs/architecture.md`](docs/architecture.md) — the whole system, data model, security model
- [`docs/deployment.md`](docs/deployment.md) — Render + Cloudinary, every variable, troubleshooting
- `/docs` on the running API — Swagger UI
