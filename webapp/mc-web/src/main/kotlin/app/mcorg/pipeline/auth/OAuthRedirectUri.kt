package app.mcorg.pipeline.auth

import app.mcorg.domain.Env
import app.mcorg.domain.Local

/**
 * The Microsoft OAuth `redirect_uri`, built in exactly one place (MCO-476).
 *
 * Microsoft compares this string byte-for-byte between the authorization request and the token
 * exchange, so the two call sites — [GetSignInPipeline]'s `getMicrosoftSignInUrl` and
 * [GetMicrosoftTokenStep] — must agree. They were two independent string literals until the port
 * became configurable, at which point "both say localhost:8080" stopped being a safe assumption.
 *
 * Note the LOCAL caveat: the URI is registered in the Azure app registration, and only
 * `http://localhost:8080/...` is. A worktree on any other port cannot complete a real Microsoft
 * sign-in — it must use the demo bypass (`SKIP_MICROSOFT_SIGN_IN=true`, the local default), which
 * is how local sign-in works in practice anyway.
 */
fun microsoftRedirectUri(env: Env, host: String?, port: Int): String =
    if (env == Local) "http://localhost:$port/auth/oidc/microsoft-redirect"
    else "https://$host/auth/oidc/microsoft-redirect"
