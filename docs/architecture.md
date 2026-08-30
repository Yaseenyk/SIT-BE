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
| Key | The Firebase API key was literal in the markup, at line 1564 |

The Firebase config, including the API key, was literal in the file at line 1564.

## 2. What it is now

```
                     GitHub Pages                         Render
        ┌──────────────────────────────┐      ┌───────────────────────────┐
        │  fe/  Next.js static export  │      │  be/  Spring Boot (Docker)│
        │  HTML + JS + CSS, no server  │─────▶│  REST API, JWT, Firestore │
        └──────────────────────────────┘ HTTPS└─────────────┬─────────────┘
                        │                                   │
                        │ signed direct upload              │
                        ▼                                   ▼
                  ┌───────────┐                     ┌───────────────┐
                  │Cloudinary │                     │   Firestore   │
                  └───────────┘                     └───────────────┘
```

**The constraint that shapes everything:** GitHub Pages serves files. There is no Node
process, so no SSR, no route handlers, no image optimiser. Content is admin-editable and
changes without a rebuild, so it is fetched **in the browser** at runtime — the same shape
the original had when it called Firestore from the page. What changed is that a server now
decides who may write.

## 3. The data model

Firestore is still the database — the **same project the original site used**, with the
same collection names, so its existing documents are read without migration. What changed
is who talks to it: the browser held the credentials and wrote directly; now only this
service does.

| Collection | Document id | Notes |
| ---------- | ----------- | ----- |
| `committees` | the slug (`technical`) | Also the `#committee-x` anchor the public site links to. Immutable |
| `members` | UUID | `committeeId` references a committee **by id** |
| `events` | UUID | Dates as ISO strings, plus a derived `lastDay` |
| `gallery` | UUID | `albumId` groups a multi-upload into one tile |
| `achievements` | UUID | — |
| `messages` | UUID | Sender IP stored **hashed**, for rate limiting only |
| `settings` | `site` | One document; the announcement lives in it |
| `adminUsers` | UUID | BCrypt hash + lockout counters |

### What Firestore does not do, and what replaced it

Moving off Postgres meant losing three guarantees that were one line of SQL each. None of
them were dropped; each is now explicit code, and each has a test.

**1. `coalesce(ends_on, starts_on) >= today` → a derived `lastDay` field.**
Firestore cannot compare two fields to each other. The naive fix is a stored `status` kept
current by a nightly job — which is exactly the original site's `autoSortEvents()` bug
rebuilt, a value correct only as often as something remembers to update it. Instead
`lastDay` is computed and written **on every save**, from data in the same document, so it
cannot drift. Dates are ISO strings because their lexicographic order is their
chronological order.

**2. `ON DELETE SET NULL` → an explicit batched unassign.**
`CommitteeService.delete` clears `committeeId` on every affected member **first**, then
deletes the committee. The ordering is the guarantee: the two writes are not atomic with
each other, and failing after the unassign leaves consistent data, whereas failing after
the delete leaves members referencing a document that is gone. And members reference a
committee by its immutable **id**, never its name — the original site matched on display
name, so a rename orphaned everyone.

**3. `order by … nulls last` and `group by` → sorting in memory.**
Firestore's `orderBy` **omits documents that lack the field entirely**, so an achievement
saved without a date would vanish from the site rather than sort last. Member counts have
no `GROUP BY` at all. These collections hold tens of documents, so both are computed in
Java: index-free, and incapable of losing a record.

`ReferentialIntegrityTest` covers all of this. It is the most important test here after the
security rules, because the failure it guards against throws nothing and displays nothing.

### What was genuinely gained

Responsibilities are an array on the committee document, so the ordered child table and its
composite key are gone. And there is no schema to migrate, no connection string, no pool,
and nothing that expires after 30 days.

### What was genuinely lost

Transactions. `@Transactional` did nothing here and was removed. A single document write is
atomic; multi-document writes use a `WriteBatch`; **anything spanning collections or
Cloudinary is not atomic at all**, and ordering is the only thing making the failure modes
survivable.

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
- **Firestore's free tier is generous but finite** — 50k document reads a day. Every page
  load costs roughly one read per collection it renders, so a normal semester stays far
  inside it. Firebase **Storage**, unlike Firestore, requires the Blaze plan for projects
  created after 30 Oct 2024; this project uses Cloudinary for images and does not need it.
- **Nothing here expires.** The Postgres this replaced was on a Render free tier that is
  deleted after 30 days, which was the practical reason for the move.
- **No cross-collection atomicity.** Deleting a committee writes to `members`, then
`committees`, then Cloudinary. A failure between them leaves members already unassigned —
visible and repairable — rather than orphaned. That ordering is deliberate; see
`CommitteeService.delete`.

**The token is in `localStorage`**, readable by any script on the origin. The accepted
  trade for a static site with no server to set an HttpOnly cookie; mitigated by the
  12-hour expiry and secret rotation.
