# Logging policy

What Seam is allowed to put in a log line, and why. Written for MCO-339 (Epic B), before log
shipping was turned on — so this is a standard to hold to, not a description of a cleanup.

## The posture: pseudonymous

**Minecraft UUIDs and usernames are acceptable in logs. Nothing else that identifies a person is.**

A Minecraft username is a public in-game identity — visible to everyone on any server the player
joins — and it is the entire domain of this app. It is also the only practical handle for debugging
a sign-in problem someone reports. Trading it away buys little privacy and costs a lot of
debuggability.

Everything else is out: email addresses, request query strings, raw database driver messages, and
user-authored content (project names, idea text).

There is deliberately **no real email anywhere in the system** — see below.

## Rules

1. **Never log an exception whose message can carry data.** The rendered stack trace starts with
   `getMessage()`, so `logger.error("...", e)` leaks exactly as much as interpolating the message.
   Two known offenders, both handled:
   - kotlinx-serialization appends `"\nJSON input: " + input` to decoding failures
     (`exceptionsWithDebugInfo` defaults to true). See `ApiProvider.logDeserializationFailure`.
   - PostgreSQL appends `DETAIL: Key (column)=(value) already exists`. See
     `DatabaseSteps.logDatabaseFailure`, which keeps SQLState, constraint, table and routine and
     drops the message.
2. **Log `request.path()`, never `request.uri`.** `uri` includes the query string; `path()` does
   not. Ktor's `CallLogging.defaultFormat` already uses `path()`.
3. **Never log a response body from an upstream API.** Status, URL and byte count only. A truncated
   body at DEBUG is acceptable, because DEBUG is off in production.
4. **Never log whole domain events or entities.** `SeamEvent` payloads carry project and idea names.
   Log the type plus opaque ids.
5. **No `println` / `System.out` / `System.err` outside `cli/`.** They bypass logback entirely, so
   no level policy, filter or appender can ever suppress them.
6. **Levels are a safety mechanism, not just noise control.** See `logback.xml`: root defaults to
   INFO via `${LOG_LEVEL:-INFO}`, and `app.mcorg` has its own `${APP_LOG_LEVEL:-INFO}` because
   `ValidationSteps` logs raw request parameter values at DEBUG. Neither goes below INFO in
   production.

7. **Secret-bearing types get a redacting `toString()`.** Any data class holding a token, a bearer
   or HMAC secret, or a verbatim upstream body overrides `toString()` using
   `app.mcorg.logging.redacted(...)`, which renders `<redacted:N>`. The generated `toString()` on a
   data class prints every field, so a type without an override is one `logger.debug("$thing")`
   from leaking. `RedactionTest` seeds a secret into each and asserts it does not survive.
8. **MDC keys are an allowlist.** `ALLOWED_MDC_KEYS` in `Monitoring.kt` is the set permitted to
   reach the encoder. Today's pattern renders only `%X{call-id}`, so anything else is silently
   dropped — but a JSON encoder emits the *entire* MDC map, so adding a key is a decision to
   publish it.
9. **Caller-supplied input never lands in a log field unvalidated.** `X-Request-Id` becomes the
   call id and is rendered on every line; `isAcceptableCallId` constrains it to a charset and 64
   characters, so a caller cannot forge a log line or blow up field cardinality.

## Site-by-site decisions

| Site | Contains | Decision |
| --- | --- | --- |
| `CreateUserIfNotExistsStep` | Minecraft UUID + username at every sign-in, old→new on rename | **Accepted.** Public in-game identity, and the primary debugging handle for sign-in. |
| `InProcessEventBus` | previously the whole `SeamEvent` | **Fixed.** Now handler name, event type, worldId, actorId. Payloads carry user-authored project and idea names. |
| `RolePlugins` (demo-user block) | username + full request URI | **Fixed.** Now `path()`. |
| `DatabaseSteps` (5 sites) | raw `SQLException` | **Fixed.** SQLState + constraint + table + routine; message withheld. Non-SQL exceptions keep their stack trace — that is code locations, not row data. |
| `ApiProvider` deserialization | previously `println(e.message)` with OAuth tokens | **Fixed** (MCO-336). Target type + URL only. |
| `ApiProvider` non-2xx | previously an unbounded upstream error body | **Fixed** (MCO-338). Status + URL + byte count; truncated body at DEBUG only. |
| `ErrorBoundary` (`defaultHandleError`) | previously nothing at all | **Added** (MCO-350). Failure type, method, `path()`, user id. No exception is attached — the `AppFailure` variants are `data object`s with no cause field, so there is nothing to leak. Levelled so ordinary outcomes stay quiet: validation, redirects and a missing token are silent, an expired token and a missing row are INFO, a refused authorization is WARN. |
| `GetServerFileStep` timeout | version + elapsed budget | **Accepted** (MCO-346). No URL body, no exception on the timeout paths — a `SocketTimeoutException` message is "Read timed out" and carries nothing. |

### The call id is user-visible

Since MCO-350 the 500 page and the generic error alert print this request's call id, so a user can
quote it in a report. That is safe *because* of the MCO-341 rules above: an inbound `X-Request-Id`
is only adopted if it matches `[A-Za-z0-9+/=_-]{1,64}`, and anything else is replaced by a
server-generated UUID. The id is opaque and says nothing about the user or the failure.

Keep it that way. If a call id ever becomes derived from something meaningful — a user id, a
session, a tenant — it stops being safe to render and this decision has to be revisited.

## On email

`users.email` existed but never held an email. Every row was created with the synthetic placeholder
`'<minecraft-uuid>@minecraft.temp'`, because the Microsoft/Xbox sign-in flow yields only a UUID and
a username and the column was `NOT NULL`. Verified against production before removal: 7 of 7 rows
were placeholders, 0 were real.

**Decision: do not collect one.** `V2_53_0__drop_users_email.sql` drops the column. Nothing consumed
it — there is no email notification path, and the only reader was an admin table column rendering
`<uuid>@minecraft.temp` to the screen. Collecting an address the product does not use would convert
a pseudonymous dataset into personal data and make every downstream question (retention, deletion,
export) harder, for no feature.

If a real use appears — account recovery, email notifications — fetch it then. `openid` is already
in the OAuth scope (`GetSignInPipeline`), so it is an `email` scope plus an `id_token` claim away.
Note that adding the scope changes the Microsoft consent screen every user sees, and that this
policy would then need a "never log the email address" line, which rule 1 already implies.

Side effects removed with the column: the admin user table no longer shows a placeholder column, and
`actorDisplayName()` no longer attributes events to `<uuid>@minecraft.temp` for a bare `Profile`
actor — it returns `null`, which `actorName` already permits.

## Retention

Retention is set when the Axiom dataset is created (MCO-343). It must match this posture: with
usernames and UUIDs in the stream, the dataset is pseudonymous personal data, so pick a bounded
window rather than indefinite retention and record the number here when it is chosen.
