# Seam webhook wire contract

**mc-org is the producer, and this file is canonical.** The contract previously lived only in the
*consumer* (`seam-discord/CLAUDE.md`), which is backwards — and it matters now that
`seam-server-dashboard/plans/discord-player-notifications.md` describes a second producer cloning
the same shape.

If this document and any code disagree, the code wins and this document is a bug. The pieces it
describes live in `mc-web/src/main/kotlin/app/mcorg/webhook/` and
`mc-web/src/main/kotlin/app/mcorg/event/EventEnvelope.kt`.

## Delivery

A delivery is an HTTP `POST` from the Seam poller to a subscription's `callback_url`, carrying one
or more event envelopes as JSON.

| | |
| --- | --- |
| Method | `POST` |
| Content-Type | `application/json` |
| Signature header | `X-Seam-Signature: sha256=<lowercase hex>` |
| Delivery ids header | `X-Seam-Delivery-Ids: <comma-separated outbox row ids>` |
| Request timeout | 5s |
| Attempts | 3 — immediate, then +30s, then +5min |
| Auto-deactivation | after 10 consecutive failures for a subscription |

### Callback URL

Producer-controlled and stored per subscription. For Discord it is built by
`buildDiscordCallbackUrl`:

```
<SEAM_DISCORD_URL>/seam-events/<channelId>[?compact=1]
```

The query string is **not** covered by the signature (which is over the body alone). That is
acceptable because nothing is acted on without a validly signed body — but it does mean a
callback URL must never carry anything security-relevant.

Callback URLs are SSRF-checked before a subscription is stored (`WebhookCallbackUrl.isSafe`), and
must be HTTPS in production.

### Signature

`X-Seam-Signature: sha256=<hex>` where `<hex>` is the lowercase hex HMAC-SHA256 of the **exact raw
request body**, keyed by the subscription's `secret`.

> **Verify over the raw received bytes.** Seam stores payloads as JSONB and round-trips them, so
> the signed body is the database-normalised JSON. Parsing and re-serialising before verifying
> will produce a different byte sequence and fail. This is the single most likely way to get a
> receiver wrong.

Compare in constant time. On failure return `401` and do nothing else.

### Delivery ids

`X-Seam-Delivery-Ids` carries the outbox row ids in this POST, comma-separated. They are stable
across retries of the same row, so a receiver can suppress a repeat.

This exists because the producer retries on a 5s timeout: a receiver that returns 2xx just after
that timeout fires has already acted on a delivery Seam is about to send again. Without a key,
that is a duplicate message with no way to detect it.

Plural because one POST may carry a batch — dedup per id rather than per request.

The header is **not** covered by the signature. It defends against Seam's own retries, not against
an attacker; an attacker who could forge it could equally forge the body, which the signature does
cover.

## Body

Two shapes, and a receiver must handle both:

- **One event** in the poll window → the bare envelope.
- **Two or more** → wrapped: `{ "events": [envelope, envelope, …] }`.

There is no `batch` flag. Normalise with something equivalent to:

```js
const envelopes = Array.isArray(payload.events) ? payload.events : [payload]
```

### Envelope

```json
{
  "event_type": "project_created",
  "world_id": 1,
  "timestamp": "2026-08-16T17:56:28Z",
  "actor": 42,
  "actor_name": "even",
  "data": { }
}
```

| Field | Type | Notes |
| --- | --- | --- |
| `event_type` | string | See the table below. |
| `world_id` | int | The world the event belongs to. |
| `timestamp` | string | ISO-8601 instant, UTC. |
| `actor` | int \| null | User id, or `null` for a system-originated action. |
| `actor_name` | string \| null | Display name. `null` when the actor is a system action *or* when the name is simply not populated — do not infer "system" from this field, use `actor`. |
| `data` | object | Event-specific; shape varies by `event_type`. |

`actor_name` is real and has been sent since MCO-228, but `seam-discord/CLAUDE.md` omits it from
its envelope list. Consumers should treat unknown fields as ignorable regardless.

## Event types

Eleven types are emitted (`SeamEvent.kt`). The five marked ✅ are the ones `seam-discord` currently
renders; see [Subscription filters](#subscription-filters).

| `event_type` | Rendered by seam-discord |
| --- | --- |
| `project_created` | ✅ |
| `project_status_changed` | ✅ |
| `project_resources_complete` | ✅ |
| `project_unblocked` | ✅ |
| `resource_milestone_reached` | ✅ |
| `resource_count_updated` | — |
| `task_toggled` | — |
| `production_path_generated` | — |
| `dependency_edge_added` | — |
| `dependency_edge_removed` | — |
| `idea_imported` | — |

## Responding

Return **2xx quickly** once you have accepted the delivery — do the slow work afterwards (in a
Cloudflare Worker, `ctx.waitUntil`). The poller marks the row `DELIVERED` on any 2xx.

Anything else — non-2xx, a connection error, or exceeding the 5s timeout — counts as a failure:

1. The row's attempt count increments and it is rescheduled: +30s, then +5min.
2. After 3 attempts the row is marked `FAILED` and never retried.
3. Independently, 10 **consecutive** failures deactivate the subscription entirely.

That last one is the sharp edge: **a receiver that 500s on every event will be silently switched
off upstream**, and reconnecting is a manual step. Returning 2xx for an event you do not care
about is correct behaviour; returning an error is not.

## Subscription filters

A subscription stores an `event_filter` array. `["*"]` matches everything; otherwise the filter is
an explicit list of `event_type` values (`eventMatchesFilter`).

**Decision (MCO-358): new Discord subscriptions are created with the five rendered types, not
`["*"]`.**

The reasoning is not primarily bandwidth. Every delivery is an outbox row, an HMAC, and a POST
with a 5s timeout — and, more importantly, *a failure on an unrendered event counts toward the
10-consecutive-failure auto-deactivation exactly as much as a failure on one that matters*. Under
`["*"]`, the chattiest events in the system (`resource_count_updated`, `task_toggled`) were the
ones the Worker discarded, so the majority of the deactivation budget was being spent on messages
nobody would ever see.

The cost of this decision is a coupling that must be maintained by hand: when `seam-discord` learns
to render a new type, `DISCORD_RENDERED_EVENTS` in `DiscordSettingsPipeline.kt` has to be widened,
**and existing subscriptions must be reconnected** — a stored filter is not retroactively changed.
Other consumers are unaffected and may still subscribe with `["*"]`.

## Versioning

**Decision (MCO-358): no `version` field. Additive changes only, and a breaking change gets a new
callback path.**

`EventEnvelope.kt` used to say "envelope versioning is deferred until the first schema change",
which is a decision not to decide. The resolution:

- **Additive is always allowed.** New envelope fields, new `data` keys, new `event_type` values.
  Consumers must ignore unknown fields and unknown event types — every current consumer already
  does, and a receiver that does not is broken regardless of what this document says.
- **Anything else is breaking**, and a breaking change ships on a new callback path (`/v2/…`)
  rather than by mutating what existing subscriptions receive. This is available to us precisely
  because the callback URL is producer-controlled and stored per subscription, so old and new can
  run side by side and subscriptions migrate individually.

A `version` field was considered and rejected. It costs nothing to add, but it also does nothing
until a consumer branches on it, and a field that is present-but-unread is a false signal of
compatibility-handling that does not exist. Path-based versioning cannot be ignored by accident.

If a future consumer genuinely needs to negotiate, add the field then — it is an additive change,
which this policy permits.

## Related

- `documentation/logging.md` — what may appear in a log line; delivery bodies and secrets may not.
- `seam-discord/CLAUDE.md` — the consumer. Should point here rather than restating the contract.
- MCO-357 — outbox claiming, the `IN_FLIGHT` status, and the delivery-ids header.
