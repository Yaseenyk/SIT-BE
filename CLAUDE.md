# CLAUDE.md — AISA

AI guardrails for this repository. These rules are binding for every code generation,
refactor, and review in this project. When a rule here conflicts with a general habit,
this file wins.

---

## 0. Project identity

**AISA** — the AIML Student Association site for the Department of CSE (AI & ML),
Dr. Bapuji Salunkhe Institute of Engineering & Technology, Kolhapur.

Two deployables, one repository:

| Directory | What it is | Where it runs |
| --------- | ---------- | ------------- |
| `fe/` | Next.js (App Router) + TypeScript + Tailwind v4, **static export** | GitHub Pages |
| `be/` | Spring Boot 3 + Java 25 + **Cloud Firestore** + **Firebase Auth** | Render (Docker) |

It replaces a single 4,019-line `AISA_Website .html` that called Firebase (Firestore,
Auth, Storage) directly from the browser. Read `docs/architecture.md` before changing
anything structural — it records what moved where, and why.

**The one constraint that shapes everything:** GitHub Pages serves static files. There is
no Node process on the frontend and no server-side rendering of content. Every piece of
admin-editable content is fetched in the browser from the Java API.

---

## 1. The security model (hard rule)

**Public reads. Students act on their own behalf. Admins do everything else.
Enforced on the server, nowhere else.**

- Authorisation lives in `be/.../config/SecurityConfig.java` as *rules*, not as
  annotations sprinkled across controllers. A rule you cannot see in that one file does
  not exist. Do not add `@PreAuthorize` to individual handlers as a substitute.
- `SecurityRulesIntegrationTest` is the test that must never be weakened. Adding an
  endpoint means adding its case there. A write endpoint reachable anonymously is the
  worst bug this project can ship, and it throws no exception.
- **Nothing in `fe/` is a security boundary.** `AdminGate` decides which screen to show,
  not what anyone is allowed to do. Never move a check into the client and delete the
  server-side one.
- **Identity is Firebase Auth; authorisation is ours.** Firebase answers "who is this";
  `SecurityConfig` answers "what may they do". Never move an authorisation decision into
  Firebase custom claims or into the client — that is precisely what the original
  single-file site did wrong.
- **Signing up can only ever produce a STUDENT.** `UserService.register` assigns the role
  and ignores anything the client sends. A public form that can grant admin is the same
  bug as an unauthenticated write endpoint. The first admin comes from `ADMIN_EMAIL` /
  `ADMIN_PASSWORD` at boot; every later one is promoted by an existing admin.
- **No token is stored by us.** The API client pulls a fresh Firebase ID token per request
  (`setTokenProvider` in `lib/api/client.ts`); Firebase keeps its refresh token in
  IndexedDB. Do not reintroduce a token in `localStorage`.
- A valid token is not permission. `FirebaseAuthenticationFilter` grants one of
  `ROLE_UNREGISTERED`, `ROLE_UNVERIFIED`, `ROLE_SUSPENDED`, `ROLE_STUDENT`, `ROLE_ADMIN`,
  and `MeResponse.state` reports the same thing to the client. **The client must never
  re-derive that state** — it did once, drifted immediately, and a refused signup showed
  as a successful one.

### Secrets

- Every secret lives in `be/.env` (git-ignored) and in the host's environment. Nowhere
  else. `be/.env.example` documents each one and is the file you update when adding a new
  one.
- **Anything named `NEXT_PUBLIC_*` is compiled into the JavaScript the browser downloads.**
  It is not a secret and never can be. If you find yourself wanting a secret in `fe/`, the
  work belongs in `be/`.
- The old file had a live Firebase API key committed in it. Do not reintroduce that
  pattern for any provider.

---

## 2. Frontend rules

### Design tokens are mandatory

The palette is a straight port of the original site's CSS custom properties, declared once
in `src/app/globals.css` under `@theme`.

```tsx
// ❌ never
<span className="text-[#b3861a]">Events</span>
<div style={{ background: "#07294d" }} />

// ✅ always
<span className="text-gold">Events</span>
<div className="bg-navy" />
```

- Surfaces: `page` (warm off-white, never `#fff`), `surface`, `sunken`, `paper` (cards).
- Structure: `navy-deep`, `navy`, `navy2`, `navy3`, `navy-tint`.
- Accent: `gold`, `gold-bright` (on navy only), `gold-soft`; `clay` / `clay-soft` as the
  third colour where navy-and-gold alone becomes monotonous.
- Status, semantic only: `green`, `amber`, `red` and their `-soft` pairs.
- Text and rules: `ink`, `body`, `muted`, `rule`, `rule-strong`.
- Elevation is a scale, not one shadow: `shadow-raise`, `shadow-lift`, `shadow-float`.
- Fonts: `font-serif` (Source Serif) for headings, `font-sans` (Inter) for body,
  `font-mono` for numbers and labels.
- Composed classes in `globals.css`: `.card`, `.card-hover`, `.band-navy`, `.pattern-dots`,
  `.image-placeholder`, `.image-scrim`, `.reveal`, `.section-rule`.

- **The two documented exceptions** are `committee.gradient` and the hero's radial wash:
  both are per-record values authored by an admin or one-off art direction, so they can
  only be inline styles. Everything else that reaches for `style={{}}` is a mistake.
- **Two failed directions are recorded at the top of `globals.css`. Read them.** The first
  build looked machine-generated (gradient headings, glow, particles); stripping all of it
  produced a page that looked like a government form. Depth, warmth and pictures are what
  fixed it — not ornament.
- If a value you need genuinely has no token, **propose a token** rather than reaching for
  an arbitrary value.

### Tailwind only scans `src/`

`globals.css` pins the scan with `@import "tailwindcss" source(none);` + `@source "../../src";`.
Tailwind v4 otherwise scans the whole project, sweeps up this file and `docs/*.md` — which
*name* utility classes while discussing them — and compiles them into the bundle. **Do not
remove that pair.**

### Never build a class name from a variable

Tailwind scans source text. `bg-${tone}/10` is not in the output CSS and renders unstyled.
Use a full-string lookup map, as `Badge` in `components/ui/primitives.tsx` does.

### RSC by default

- Every component is a Server Component until proven otherwise. `"use client"` belongs at
  the lowest leaf that actually needs it.
- `app/page.tsx` is and stays a Server Component: it is composition only.
- Sections are client components because they fetch at runtime — that is inherent to a
  static export, not laziness. Do not "fix" it by fetching in `page.tsx`; there is no
  server to fetch on.

### Data fetching

- Components never build a URL. Every call goes through `lib/api/endpoints.ts`.
- Every fetch goes through `useApi`, which gives loading / error / retry. Loading and
  failure are **normal reachable states** here (the free-tier API sleeps), not edge cases.
  A section that renders nothing on failure is incomplete work.
- `useApi` deps must be **primitives**. They are joined into a string key.

### Accessibility

- One `<h1>` per page (the hero). Heading levels never skip.
- Every interactive element is a real `<button>` or `<a>` and is keyboard-operable.
  A `<div onClick>` is a bug.
- Decorative canvases and icons get `aria-hidden`.
- `prefers-reduced-motion` is honoured — `globals.css` kills every animation AND forces
  `.reveal` visible. A reveal that animates from `opacity: 0` and never runs is a section
  nobody can read, so the override is not optional.

---

## 3. Backend rules

### Firestore has no schema, so the code is the schema

- There are no migrations, because there is nothing to migrate. Every document is mapped
  by hand in a repository (`toX` / `toMap`), never by the SDK's reflective POJO mapper —
  which reads `null` for a renamed field instead of failing, and `null` is legitimate for
  most fields here, so nothing would surface until a page rendered blank.
- **Firestore has no cascade and no foreign keys.** Deleting anything means deleting what
  pointed at it, in the same service method, and `ReferentialIntegrityTest` is what proves
  it. A new collection that references another means wiring that path too.
- Derived fields used by queries (`Event.lastDay`) are written on EVERY save, or a query
  silently misses documents saved before the field existed.

### Layering

`Controller` → `Service` → `Repository`. Controllers do routing, validation
(`@Valid`) and status codes; they contain no business logic. Services own transactions.

### Never serialise an entity

Controllers return DTOs (`*Dtos.java` or a record nested in the service). Returning an
entity leaks admin-only columns — the Cloudinary `public_id` fields, the notification
email — and couples the JSON shape to column names.

### Errors

One shape, from `GlobalExceptionHandler`. Throw `NotFoundException`, `ConflictException`,
`RateLimitedException`, or `IllegalArgumentException` and let it map. Never build an error
body in a controller. Never let a stack trace reach the client.

### Validation at the boundary

Bean Validation on the request record. Business rules that a constraint cannot express
(an end date before a start date) are checked in the service with a readable message,
*and* backed by a database constraint. Not in between.

---

## 4. Images

Uploads go **browser → Cloudinary**, signed by `POST /api/v1/media/signature`. The bytes
never pass through the API.

- The API secret never reaches the browser.
- The resize is inside the signed transformation, so a client cannot skip it.
- Deleting a record deletes its image (`MediaService.deleteQuietly`). Replacing an image
  releases the old one. **A new image field means wiring both paths** — otherwise orphaned
  assets accumulate where nobody will ever find them.
- `deleteQuietly` logs rather than throws, on purpose: a failed remote cleanup must not
  fail the admin's delete.

---

## 5. Documentation is part of the change

- `docs/architecture.md` — what moved from the HTML file, the data model, the request flow
- `docs/deployment.md` — Pages + Render, and every environment variable
- `docs/ui-system.md` — the token table and the component inventory

**Read the relevant doc before touching an area. Update it in the same change that makes
it stale.** A change to the API contract that leaves `docs/architecture.md` describing the
old shape is not done.

---

## 6. Working agreement

- **Surgical changes only.** Touch what the task requires. No drive-by refactors, no
  speculative abstractions.
- **Comments explain *why*, never *what*.** The code already says what it does. Every
  comment in this codebase earns its place by recording a decision, a constraint, or a bug
  that the obvious alternative would cause — several record what the original single-file
  site got wrong. Match that bar.
- **No speculative error handling.** Validate at real boundaries — user input, the API,
  Cloudinary. Not in between.
- **Verify before claiming done.** If you cannot run something, say so plainly instead of
  implying success.

---

## 7. Definition of done

**Frontend**
- [ ] `npm run typecheck`, `npm run lint`, `npm test`, `npm run build` all pass
- [ ] Only design tokens; zero arbitrary colour values
- [ ] `"use client"` only where genuinely needed, at the leaf
- [ ] Loading, error and empty states all handled
- [ ] Semantic HTML; single `h1`; keyboard-operable

**Backend**
- [ ] `mvn verify` passes — needs BOTH emulators:
      `npx firebase-tools emulators:start --only firestore,auth` (from `be/`)
- [ ] Deleting a record deletes what referenced it — Firestore has no cascade
- [ ] New endpoints have a case in `SecurityRulesIntegrationTest`
- [ ] DTOs in, DTOs out — no entity crosses the controller boundary
- [ ] New config is in `.env.example` **and** `docs/deployment.md`

**Both**
- [ ] Affected `docs/` updated in the same change
- [ ] No secret added to `fe/`, and none committed anywhere
