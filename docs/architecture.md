# Architecture

How the single-file site became two deployables, and what changed on the way.

---

## 1. What it was

`AISA_Website .html` — 4,019 lines in one file:

| Layer | In the original |
| ----- | --------------- |
| Markup | Bootstrap 5 (CDN) + 8 sections + ~470 lines of admin modals |
| Styling | ~700 lines of CSS driven by `:root` custom properties |
| Behaviour | ~2,480 lines: Three.js hero, 2D neuron canvas, admin CRUD, browser-side image compression, arithmetic captcha |
| Backend | Firebase **compat** SDK, called straight from the browser — Firestore (7 collections), Auth, Storage |

The Firebase config, including the API key, was literal in the file at line 1564.

## 2. What it is now

```
                     GitHub Pages                         Render
        ┌──────────────────────────────┐      ┌───────────────────────────┐
        │  fe/  Next.js static export  │      │  be/  Spring Boot (Docker)│
        │  HTML + JS + CSS, no server  │─────▶│  REST API, JWT, Flyway    │
        └──────────────────────────────┘ HTTPS└─────────────┬─────────────┘
                        │                                   │
                        │ signed direct upload              │
                        ▼                                   ▼
                  ┌───────────┐                     ┌───────────────┐
                  │Cloudinary │                     │  PostgreSQL   │
                  └───────────┘                     └───────────────┘
```

**The constraint that shapes everything:** GitHub Pages serves files. There is no Node
process, so no SSR, no route handlers, no image optimiser. Content is admin-editable and
changes without a rebuild, so it is fetched **in the browser** at runtime — the same shape
the original had when it called Firestore from the page. What changed is that a server now
decides who may write.

## 3. Collections → tables

| Firestore | Table | What changed |
| --------- | ----- | ------------ |
| `committees` | `committee` + `committee_responsibility` | Responsibilities are ordered rows, not a JSON array |
| `members` | `member` | `committee_id` is a real **foreign key** |
| `events` | `event` | Real `DATE`; upcoming/past is **derived**, not stored |
| `gallery` | `gallery_item` | Album grouping kept (`album_id`) |
| `achievements` | `achievement` | — |
| `messages` | `contact_message` | Sender IP stored **hashed**, for rate limiting only |
| `settings/site` + `settings/announcement` | `site_settings` | One row, one request |
| Firebase Auth + `settings/admin` | `admin_user` | Username no longer publicly readable |

### The three changes worth knowing

**1. Members belong to committees by key.**
Firestore stored the committee's display *name* on each member and matched on that string.
Renaming a committee silently orphaned every member on it, with no error anywhere. The FK
is `ON DELETE SET NULL`, not `CASCADE`: deleting a committee must not delete the students
on it. They surface as "Unassigned" in the dashboard.

**2. Event dates are dates.**
The original stored `'Oct 10–12, 2024'` as a string in one of two hardcoded arrays, and
ran `autoSortEvents()` on every page load to move items between them by parsing that prose.
The split therefore depended on somebody opening the page, and a differently-formatted date
stayed in the wrong list forever. Now: `starts_on DATE`, optional `ends_on`, and
`status` computed per request against `Asia/Kolkata`. `date_label` preserves a
human-written string (and the imported ones) for display only.

**3. Settings are one row.**
Two documents meant two round trips on every page load for data the public site always
needs together. The announcement's expiry is now applied **server-side** — the original
compared it against the visitor's own clock, so a device with the wrong date kept showing
an announcement that had ended weeks earlier.

## 4. Authorisation

The whole model is one sentence: **public reads, admin writes.**

It is expressed as rules in `SecurityConfig`, not as annotations on ~30 handlers where one
forgotten annotation is an open write endpoint no test would notice.

```
OPTIONS  /**                       permit   (CORS preflight)
GET      /actuator/health, /docs   permit
POST     /api/v1/auth/login        permit   (rate-limited per account)
POST     /api/v1/messages          permit   (honeypot + per-IP rate limit)
GET      committees, members,      permit   ← the public reads, ENUMERATED
         events, gallery,
         achievements, settings,
         stats
*        everything else           ADMIN
```

**Public reads are listed, not blanket-permitted.** The first version of this used
`GET /api/v1/**` permitAll with the private paths carved out above it, and
`GET /api/v1/settings/admin` matched it — serving the staff notification address to
anonymous callers, with nothing anywhere to reveal it. Enumerating means a newly added
admin GET is private by default: the failure mode becomes a 401 somebody reports, rather
than a leak nobody sees. `settings` and `stats` are matched **without** a trailing
wildcard so their `/admin` variants fall through.

A side effect worth knowing: an unmatched path returns **401, not 404**, because it falls
through to the final rule. That is the better answer — it stops anonymous callers probing
which endpoints exist.

**What replaced Firestore rules.** The old rules allowed writes from any authenticated
client — and the client held the credentials. The captcha and attempt counter that guarded
the login form were browser variables: `attempts` reset on reload and `capChecked` could be
set from the console. Lockout is now per-account, in the database (`AdminUser`), where it
cannot be edited away.

**Tokens.** A 12-hour HS256 JWT carrying only the admin's id and username. There is no
`/logout`: the token is stateless, so signing out *is* discarding it. Rotating `JWT_SECRET`
revokes every issued token at once — that is the revocation mechanism.

**The frontend is not a security boundary.** `AdminGate` chooses which screen to render.
Anyone can load the dashboard markup by editing their own JavaScript; it will be empty,
because every request it makes needs a token the server validates.

## 5. Images

`browser → Cloudinary`, authorised by a signature from `POST /api/v1/media/signature`.

The bytes never pass through the API. On a free-tier container that is not an optimisation
but a requirement: it has neither the memory to buffer several 8 MB uploads nor a disk that
survives a redeploy. The folder and the resize are inside the *signed* set, so a tampered
request fails Cloudinary's own check — the old Firebase Storage path let any authenticated
client upload anything, of any size, to any path.

Deleting a record deletes its image; replacing one releases the old asset. Cleanup logs
rather than throws: a failed remote call must not fail the admin's delete.

## 6. What was deliberately not ported

**The Three.js WebGL hero (`initHero3D`, ~170 lines).** The original already fell back to
the same 2D neuron canvas whenever the CDN was unreachable, so that canvas is a *supported*
rendering of the hero, not a degraded one. Using it everywhere saves ~600 KB on a static
site. `NeuronCanvas` renders it at a higher density in the hero. To restore the WebGL
variant: add `three`, dynamically import it in a client component, and keep `NeuronCanvas`
as the fallback.

**Bootstrap.** Replaced entirely by Tailwind. Modals are the native `<dialog>` element,
which gives focus trapping, Escape-to-close and the top layer for free.

**Browser-side image compression (`compressImage`).** Superseded by the signed Cloudinary
transformation, which a client cannot skip.

**The arithmetic captcha.** Superseded by server-side per-account lockout. See §4.

## 7. Request flow, end to end

```
Visitor loads /
  └─ static HTML (metadata, JSON-LD, layout) — no content yet
     └─ SettingsProvider          GET /api/v1/settings   (one call, shared by 4 consumers)
     └─ Hero                      GET /api/v1/stats
     └─ Structure                 GET /api/v1/committees + /members
     └─ Events                    GET /api/v1/events?status=upcoming
     └─ Gallery                   GET /api/v1/gallery
     └─ Achievements              GET /api/v1/achievements

Admin saves a member with a new photo
  └─ POST /api/v1/media/signature          (admin only)
  └─ POST cloudinary.com/.../image/upload  (browser → Cloudinary, signed)
  └─ PUT  /api/v1/members/{id}             { photoUrl, photoPublicId, ... }
       └─ old public_id, if changed, is deleted from Cloudinary
```

## 8. Known trade-offs

- **Content is invisible to crawlers that do not run JavaScript.** Inherent to a static
  export with runtime data. The `Organization`/`WebSite` JSON-LD and all page metadata are
  in the static HTML; the committee list is not. Moving `fe/` to a Node host would fix it
  and cost the free hosting.
- **The free API tier sleeps** and takes ~50 s to wake. `ApiError` says so by name on
  timeout rather than showing a generic failure.
- **Render's free Postgres expires after 30 days.** For a site that must survive a
  semester, move to a paid tier, Supabase, or Neon. See `deployment.md`.
- **The token is in `localStorage`**, readable by any script on the origin. The accepted
  trade for a static site with no server to set an HttpOnly cookie; mitigated by the
  12-hour expiry and secret rotation.
