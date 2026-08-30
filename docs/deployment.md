# Deployment

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

`be/render.yaml` describes the web service. Render → **New → Blueprint** → point at this
repository.

**There is no database service.** Data lives in Cloud Firestore, which Google hosts, so
Render runs only the Java process — nothing to provision, nothing to connect, and none of
the connection-string conversion this step used to require.

### Getting the service account

Firebase Console → **Project settings → Service accounts → Generate new private key**.
That downloads a `.json` file.

**It is a private key.** Never commit it, never put it in the frontend, never paste it
into a chat. Base64-encode it before pasting into Render, because the raw JSON contains a
PEM key full of newlines that a single-line dashboard field will mangle — producing an
"invalid key" error that points nowhere near the cause:

```bash
base64 -w0 service-account.json      # Linux / Git Bash
```

### Environment variables

| Variable | Value | Notes |
| -------- | ----- | ----- |
| `FIREBASE_PROJECT_ID` | e.g. `aisa-website-24279` | Firebase Console → Project settings → General |
| `FIREBASE_SERVICE_ACCOUNT` | the service-account JSON, **base64-encoded** | A PRIVATE KEY. Base64 because the raw JSON's newlines are mangled by single-line dashboard fields |
| `CORS_ALLOWED_ORIGINS` | `https://<user>.github.io` | Exact origin, no trailing slash, no path |
| `AUTH_ALLOWED_EMAIL_DOMAINS` | `bsiet.org` | Who may sign up. Comma-separated. **Empty means anyone** — a real choice, but a deliberate one |
| `ADMIN_EMAIL` | the first admin's address | Used once, at boot |
| `ADMIN_PASSWORD` | ≥10 characters | **Set once, then clear it** (below) |
| `CLOUDINARY_CLOUD_NAME` / `_API_KEY` / `_API_SECRET` | from step 1 | Optional; image upload returns 503 without them |
| `SPRING_PROFILES_ACTIVE` | `prod` | |

There is **no database URL and no JWT secret**. Data is in Firestore and tokens are issued
and signed by Firebase, both reached with the one service account above.

`PORT` is injected by Render. Do not set it.

### Creating the first admin

**Before anything works:** Firebase Console → **Authentication → Sign-in method** →
enable **Email/Password**. Nothing else needs turning on; Google sends the password-reset
and verification mail with no configuration.

Signing up can only ever produce a *student* — the server assigns the role and ignores
what the client sends, because a public form that can grant admin is the same bug as an
unauthenticated write endpoint. So the first admin comes from the environment:

1. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD`, and deploy.
2. On boot, if no admin exists, the account is created and the log says so.
3. Sign in at `/admin/`, then **Account → Email me a password reset link**.
4. **Clear `ADMIN_PASSWORD`** and redeploy.

Every later admin is promoted from the dashboard: **Accounts → Make admin**.

If an account already exists for `ADMIN_EMAIL`, it is *promoted* rather than recreated and
`ADMIN_PASSWORD` is ignored — so this cannot be used to take over someone's account by
resetting their password.

If both variables are unset and no admin exists, the app starts normally and logs a
warning. The public site is unaffected; only the dashboard is unreachable. That is the
correct behaviour on every later redeploy.

### Verifying

```bash
curl https://aisa-api.onrender.com/actuator/health        # {"status":"UP"}
curl https://aisa-api.onrender.com/api/v1/committees      # the seeded committees
```

API docs (Swagger UI) are at `/docs`.

---

## 3. Frontend on GitHub Pages

### Repository settings

**Settings → Pages → Source: GitHub Actions.** Not "Deploy from a branch" — the workflow
uploads an artifact.

### Variables

**Settings → Secrets and variables → Actions → Variables** (the *Variables* tab, not
Secrets — both values are compiled into the browser bundle, so neither is secret and
masking them would only make a failed build harder to read):

| Variable | Example | Needed for |
| -------- | ------- | ---------- |
| `NEXT_PUBLIC_SITE_URL` | `https://<user>.github.io/SIT-FE` | Optional — derived from the repo if unset |
| `NEXT_PUBLIC_API_BASE_URL` | `https://sit-be.onrender.com` | All content |
| `NEXT_PUBLIC_FIREBASE_API_KEY` | `AIza…` | Accounts |
| `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN` | `<project>.firebaseapp.com` | Accounts |
| `NEXT_PUBLIC_FIREBASE_PROJECT_ID` | `aisa-website-24279` | Accounts |
| `NEXT_PUBLIC_FIREBASE_APP_ID` | `1:…:web:…` | Accounts |

All four Firebase values come from **Firebase Console → Project settings → General → Your
apps → Web app → SDK setup and configuration**. If there is no web app there yet, add one
(the "</>" button); it needs no hosting.

**These are public by design and belong in Variables, not Secrets.** A browser cannot talk
to Firebase Auth without them, and Google documents the web API key as a project
identifier rather than a credential: it authorises nothing. Firestore is never read from
the browser, and what a signed-in person may do is decided by the API from their `users`
document. The *private* service-account key is a different thing entirely and lives only
in Render's environment.

Without them the site still works for visitors; every account screen says accounts are not
configured, and the deploy logs a warning.

`NEXT_PUBLIC_SITE_URL` is the **single source** for `basePath` and every canonical URL —
`next.config.ts`, `src/lib/site.ts` and `scripts/postbuild.mjs` all derive from it, so they
cannot disagree.

- Project page → path is `/aisa` → `basePath` becomes `/aisa`
- Custom domain → path is empty → `basePath` is `""` and a `CNAME` file is written

Push to `main` (touching `fe/**`) or run the workflow manually.

> **Both variables are baked in at build time.** Changing either one requires a rebuild —
> use **Actions → Deploy frontend → Run workflow**.

### Custom domain

1. Set `NEXT_PUBLIC_SITE_URL=https://aisa.example.org`.
2. DNS: `CNAME` → `<user>.github.io`.
3. Re-run the workflow. `postbuild` writes `out/CNAME`; `basePath` becomes `""`.
4. Add the domain under Settings → Pages and enable **Enforce HTTPS**.
5. Update `CORS_ALLOWED_ORIGINS` on Render to the new origin.

That is one variable change. Nothing in `src/` moves.

---

## 4. Close the loop

Set `CORS_ALLOWED_ORIGINS` on Render to the real Pages origin and redeploy.

**If the site loads but every section shows an error**, this is almost always why. Open the
browser console: a CORS failure names the origin the browser sent. It must match
`CORS_ALLOWED_ORIGINS` exactly — scheme, host, no trailing slash, no path.

---

## Local development

Two terminals.

```bash
# Terminal 1 — emulators, then the API
#   npx firebase-tools emulators:start --only firestore,auth
cd be
cp .env.example .env          # then set ADMIN_EMAIL and ADMIN_PASSWORD
docker compose up -d db       # Postgres only, if running the app from an IDE
docker compose up             # or the whole stack

# Terminal 2 — frontend
cd fe
cp .env.example .env.local    # defaults already point at localhost:8080
npm install
npm run dev                   # http://localhost:3000
```

Flyway creates the schema and seeds the original content on first boot.

Image uploads need the `CLOUDINARY_*` variables. Without them the site works fully; only
the upload endpoint returns 503, with a message saying so.

### Checks

```bash
cd fe && npm run typecheck && npm run lint && npm test && npm run build
cd be && mvn verify           # needs Docker running (Testcontainers starts a Postgres)
```

---

## Operational notes

**The free API sleeps.** After ~15 minutes idle, the first request takes ~50 s. The
frontend says so by name on timeout rather than showing a generic failure. A paid instance
removes it; an uptime pinger every 10 minutes mostly hides it.

**Firestore's free tier does not expire**, unlike Render's free Postgres, which is part
of why the data lives there: a site that has to survive a semester cannot be sitting on a
30-day database. The quotas that matter here are 50k document reads and 20k writes a day,
which a department site is nowhere near — the role lookup on each authenticated request is
the largest single consumer and is still a rounding error against that.

**Back up before it matters:**

```bash
pg_dump "postgres://user:pass@host/db" > aisa-$(date +%F).sql
```

**Revoking access** is immediate: suspend or delete the account in **Accounts**. The role
is read from Firestore on every authenticated request rather than from a token claim, so
it takes effect on the caller's very next request rather than up to an hour later. That is how you
revoke a session — there is no server-side session to delete.

---

## Troubleshooting

| Symptom | Cause |
| ------- | ----- |
| Every section shows "Could not load" | `CORS_ALLOWED_ORIGINS` does not exactly match the Pages origin |
| First load fails, reload works | Free instance waking up (~50 s) |
| CSS and JS 404 on Pages | `NEXT_PUBLIC_SITE_URL` does not match the real URL, so `basePath` is wrong |
| Cannot sign in, no admin exists | Set `ADMIN_EMAIL` + `ADMIN_PASSWORD` and redeploy (see step 2) |
| "Email sign-in is not enabled for this Firebase project" | Firebase Console → Authentication → Sign-in method → enable Email/Password |
| Account screens say "Accounts are not set up yet" | The `NEXT_PUBLIC_FIREBASE_*` variables are unset. Set them and re-run the deploy workflow |
| Signup refused with "Sign up with your institute email address" | Working as intended. Change `AUTH_ALLOWED_EMAIL_DOMAINS` to widen it |
| Verified the address but still cannot register | Should self-correct: the server re-checks verification when the token claim says otherwise. If it persists, sign out and back in |
| "Too many failed attempts" | Per-account lockout, 15 minutes by default. Wait, or clear `locked_until` in the database |
| Uploads return 503 | `CLOUDINARY_*` not set on the API |
| Flyway: "checksum mismatch" | An applied migration was edited. Revert it and add a new `V<n>__…sql` |
