# Rate limiting

How Seam bounds what one client can make the webapp do, and why it is shaped this way (MCO-274).
The limits themselves are `SeamRateLimit` in
`webapp/mc-web/src/main/kotlin/app/mcorg/presentation/plugins/RateLimits.kt`; each one's KDoc says
why it is that number. This page is the strategy around them.

## What is limited, and what is not

The test for whether a route gets a limit: **can someone who is not signed in make it cost
something?** Cost means a database write, a database read (each one keeps Neon's compute awake —
the bill this project has been burned by before), or an outbound call.

| Route | Per address | What it costs without a session |
| --- | --- | --- |
| `POST /api/v1/auth/device-code` | 5 / min and 20 / hour | a `device_code` row |
| `POST /api/v1/auth/device-code/poll` | 30 / min | a read, per code sprayed |
| `/auth/oidc/*` (the sign-in callbacks) | 20 / min | an outbound round trip to Microsoft per bogus callback |
| all of `/api/v1` | 120 / min, on top of the above | a token lookup per bad bearer token |

Over a limit, a client gets `429` with `Retry-After`. Under `/api/` the body is the RFC 8628
`{"error":"slow_down"}`, which the mod's device-code poll already answers by widening its interval
and its sync already treats as "try later". Elsewhere it is a refusal like any other: an alert under
HTMX, the status page on a page load.

Device codes are also bounded in the table: every create deletes codes that expired more than a
day ago. Not at the moment of expiry, because `/link` tells a player their code "has expired" only
while the row exists.

**Not limited, deliberately:**

- **Signed-in HTML routes.** Every one of them is behind a session, and the expensive ones are
  bounded on their own terms — schematic parses are capped in size and run two at a time (MCO-426).
  Six people have accounts. The signal that brings this back is a signed-in user (or a stolen
  session) measurably loading the app — in `flyctl logs` or Neon's active time — not a hypothetical.
  When it comes, the key is the user id, not the address.
- **`/integrations/*` and `/test/ready`.** Gated by `WEBHOOK_ADMIN_SECRET`; a caller without it is
  refused before any work.
- **The sign-in page and sign-out.** A JWT check and a page — and `AuthPlugin` redirects every
  signed-out request there, so a limit would hit people with several tabs open before it hit anyone
  abusing it.
- **Static assets.** Served from memory.

## Where it runs: the app, because the edge has no rule to spare

`app.seam.gg` is proxied by Cloudflare, and Cloudflare's own rate limiting would stop a flood
before it reaches Fly. On the Free plan there is exactly **one** rate-limiting rule, matching on
path only, with a fixed 10-second counting period and a fixed 10-second block — and that rule is
taken by `mc-dashboard.seam.gg` (`seam-server-dashboard/plans/security-hardening.md` § 2). A
10-second block would not stop a steady one request per second anyway: that is 86,400 device codes
a day.

So every limit is in the app. **On a paid plan**, the device-code and sign-in limits are the ones
to move to the edge first (Pro: two rules, periods up to a minute) — they are unauthenticated, so
the edge sees everything it needs to count them.

### Why not Ktor's `RateLimit` plugin

It was the first choice, and a test showed it cannot do the one thing `/api/v1` needs. `RateLimit`
checks its bucket *after* the route's own plugins run, so a request `ApiBearerAuthPlugin` refuses —
an invalid token, after a database lookup — never spends a token, and a client spraying bad tokens
is never limited. `rateLimited { }` is a route-scoped plugin in the same phase as the auth plugins
and, declared on a parent route, runs before them. The regression test is `RateLimitsTest` → "a
request a route plugin refuses still spends the bucket".

### In memory, because there is one machine

The counters are fixed windows in a map on the one Fly machine. A restart resets them, which is
harmless. **A second machine would give every client a second allowance** — if the app is ever
scaled out, this needs a shared store (or the edge) first. Closed windows are swept once there are
more than 10,000 — at most once a minute, so a map held over that by open windows is not walked on
every request — and memory is bounded by the number of distinct clients inside an hour. A botnet
large enough to make that matter is a volumetric attack, and that is Cloudflare's job.

## Who the client is: the origin lock

A per-address limit is only as good as the address. Behind Cloudflare and Fly:

- `origin.remoteHost` is Fly's proxy — the same for everyone.
- `Fly-Client-IP` is a Cloudflare edge address — the same for everyone on that data centre.
- `CF-Connecting-IP` is the real client — **but only if the request came through Cloudflare.**
  (A Transform Rule cannot rewrite `cf-*` headers, so one that did come through is genuine.)

Fly serves the app to anyone who connects to a Fly edge address and names the host. That is
`mcorg.fly.dev`, and it is also `app.seam.gg` with the address pinned
(`curl --resolve app.seam.gg:443:<fly ip> …`, verified 2026-10-07). Removing the fly.dev hostname
would not close it, because Fly routes on the name. A request that skips Cloudflare can send its
own `CF-Connecting-IP` and pick a fresh bucket every time.

So the origin is locked (`EdgeOriginGate.kt`): a Cloudflare **Transform Rule** (Free includes ten)
adds `X-Seam-Edge: <secret>` to every request it forwards to `app.seam.gg`, and the app refuses
(403) anything without it, before routing. `CF-Connecting-IP` is read only when the lock is on. The
one exemption is `/test/ping`, because Fly's health checker reaches the machine directly.

The secret is `EDGE_ORIGIN_SECRET` — required in PRODUCTION (the app will not start without it),
unset in TEST and LOCAL, which are not behind Cloudflare and key on the peer address.

**IPv6 clients are counted per /64.** A subscriber is routinely handed a whole /64; keying on the
full address would let one client rotate through 2^64 of them.

## Setting it up, and rotating the secret

Order matters. A secret the app expects but Cloudflare does not yet send refuses every visitor.

1. Generate a value: `openssl rand -hex 32`.
2. **Cloudflare** → `seam.gg` zone → Rules → Transform Rules → *Modify Request Header* → new rule:
   when `http.host eq "app.seam.gg"`, **Set static** `X-Seam-Edge` = the value. Deploy it.
3. **Fly**: `flyctl secrets set --stage EDGE_ORIGIN_SECRET=<value> -a mcorg`. `--stage` waits for
   the next deploy instead of restarting the machine on the old image.
4. Deploy (merge to master).
5. Check: `curl -sI https://app.seam.gg/` is `200`/`302`, and `curl -sI https://mcorg.fly.dev/`
   is `403`.

To rotate without an outage the app would have to accept two values for a while; it does not
today. Rotate in a quiet moment: set the new value in Cloudflare and Fly within a minute of each
other (`flyctl secrets set` without `--stage` restarts the machine), and expect a few refused
requests in between.
