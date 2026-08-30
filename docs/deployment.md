# Deployment — SIT-BE

Frontend on GitHub Pages, backend on Render, images on Cloudinary.

> **The thing to know first:** GitHub Pages cannot run Java. It serves static files only.
> The Next.js site is exported to HTML/JS and published to Pages; the Spring Boot API runs
> somewhere that can run a process. They talk over HTTPS via `NEXT_PUBLIC_API_BASE_URL`.

---

## Order of operations

Do it in this order — each step needs a URL from the one before.

1. Cloudinary (get the three keys)
2. Backend on Render (needs the Pages URL for CORS — use a placeholder, fix in step 4)
3. Frontend on Pages (needs the API URL)
4. Go back and set the real `CORS_ALLOWED_ORIGINS`

---

## 1. Cloudinary

Free tier is ample for a college site.

1. Sign up at cloudinary.com.
2. Dashboard → **API Keys**. Copy **Cloud name**, **API Key**, **API Secret**.

The API secret **never** reaches the browser. The frontend asks the API for a short-lived
upload signature instead.

---

## 2. Backend on Render

### Using the blueprint

`render.yaml` describes both the web service and the database. Render → **New →
Blueprint** → point at this repository.

### The one manual step: DATABASE_URL

Render's connection string is `postgres://user:pass@host/db`. **JDBC does not accept that
form.** Convert it:

```
postgres://aisa:SECRET@dpg-xxxx.singapore-postgres.render.com/aisa_db
                        ↓
DATABASE_URL=jdbc:postgresql://dpg-xxxx.singapore-postgres.render.com:5432/aisa_db
DATABASE_USERNAME=aisa
DATABASE_PASSWORD=SECRET
```

Use the **Internal** database URL — it does not leave Render's network.

### Environment variables

| Variable | Value | Notes |
| -------- | ----- | ----- |
| `DATABASE_URL` | `jdbc:postgresql://…` | Converted as above |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | from Render | |
| `JWT_SECRET` | `openssl rand -base64 48` | **Required.** The app refuses to start without it. Rotating it signs everyone out |
| `CORS_ALLOWED_ORIGINS` | `https://<user>.github.io` | Exact origin, no trailing slash, no path |
| `CLOUDINARY_CLOUD_NAME` / `_API_KEY` / `_API_SECRET` | from step 1 | |
| `ADMIN_BOOTSTRAP_USERNAME` | `AISA2026` | |
| `ADMIN_BOOTSTRAP_PASSWORD` | a strong password, ≥10 chars | **Set once, then clear it** (below) |
| `SPRING_PROFILES_ACTIVE` | `prod` | |

`PORT` is injected by Render. Do not set it.

### Creating the first admin

There is no admin until you make one, and the password is never in the repository.

1. Set `ADMIN_BOOTSTRAP_PASSWORD` and deploy.
2. On boot, if the admin table is empty, the account is created and the log says so.
3. Sign in at `/admin/`, then **Account → Change password**.
4. **Clear `ADMIN_BOOTSTRAP_PASSWORD`** and redeploy.

If the variable is unset and no admin exists, the app starts normally and logs a warning.
That is the correct behaviour on every later redeploy.

### Verifying

```bash
curl https://sit-be.onrender.com/actuator/health        # {"status":"UP"}
curl https://sit-be.onrender.com/api/v1/committees      # the seeded committees
```

API docs (Swagger UI) are at `/docs`.

---

## 3. Frontend on GitHub Pages

The frontend is a separate repository: **https://github.com/Yaseenyk/SIT-FE**.
Its README has the full walkthrough. In short:

1. Settings → Pages → Source: **GitHub Actions**.
2. Settings → Secrets and variables → Actions → **Variables**:
   - `NEXT_PUBLIC_SITE_URL` — e.g. `https://yaseenyk.github.io/SIT-FE`
   - `NEXT_PUBLIC_API_BASE_URL` — this API's origin, e.g. `https://sit-be.onrender.com`
3. Push to `main`, or run the workflow manually.

Both are compiled into the browser bundle at build time, so changing either needs a
rebuild.


## 4. Close the loop

Set `CORS_ALLOWED_ORIGINS` on Render to the real Pages origin and redeploy.

**If the site loads but every section shows an error**, this is almost always why. Open the
browser console: a CORS failure names the origin the browser sent. It must match
`CORS_ALLOWED_ORIGINS` exactly — scheme, host, no trailing slash, no path.

---

## Local development

Two terminals, two checkouts.

```bash
# Terminal 1 — this repo: API + Postgres
cp .env.example .env          # set JWT_SECRET and ADMIN_BOOTSTRAP_PASSWORD
docker compose up -d db       # Postgres only, if running the app from an IDE
docker compose up             # or the whole stack

# Terminal 2 — the SIT-FE checkout
cp .env.example .env.local    # defaults already point at localhost:8080
npm install
npm run dev                   # http://localhost:3000
```

Flyway creates the schema and seeds the original content on first boot.

Image uploads need the `CLOUDINARY_*` variables. Without them the site works fully; only
the upload endpoint returns 503, with a message saying so.

### Checks

```bash
mvn verify        # needs Docker running (Testcontainers starts a real Postgres)
```

The frontend has its own checks — see the SIT-FE README.

---

## Operational notes

**The free API sleeps.** After ~15 minutes idle, the first request takes ~50 s. The
frontend says so by name on timeout rather than showing a generic failure. A paid instance
removes it; an uptime pinger every 10 minutes mostly hides it.

**Render's free Postgres expires after 30 days** and cannot be renewed. For a site that
must survive a semester, either move to Render's paid tier or point `DATABASE_URL` at
Supabase or Neon, whose free tiers do not expire. Nothing in the code changes — it is
plain PostgreSQL.

**Back up before it matters:**

```bash
pg_dump "postgres://user:pass@host/db" > aisa-$(date +%F).sql
```

**Rotating `JWT_SECRET`** invalidates every issued token immediately. That is how you
revoke a session — there is no server-side session to delete.

---

## Troubleshooting

| Symptom | Cause |
| ------- | ----- |
| Every section shows "Could not load" | `CORS_ALLOWED_ORIGINS` does not exactly match the Pages origin |
| First load fails, reload works | Free instance waking up (~50 s) |
| CSS and JS 404 on Pages | `NEXT_PUBLIC_SITE_URL` does not match the real URL, so `basePath` is wrong |
| App will not start: "JWT_SECRET must decode to at least 32 bytes" | Generate one: `openssl rand -base64 48` |
| Cannot sign in, no admin exists | Set `ADMIN_BOOTSTRAP_PASSWORD` and redeploy (see step 2) |
| "Too many failed attempts" | Per-account lockout, 15 minutes by default. Wait, or clear `locked_until` in the database |
| Uploads return 503 | `CLOUDINARY_*` not set on the API |
| Flyway: "checksum mismatch" | An applied migration was edited. Revert it and add a new `V<n>__…sql` |
