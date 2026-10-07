package app.mcorg.webhook

import app.mcorg.config.AppConfig
import app.mcorg.domain.Env
import app.mcorg.domain.Production
import java.security.MessageDigest

/**
 * Which subscriptions this app may deliver to.
 *
 * Every database outside production is a fork of production: a worktree's Neon branch and a PR
 * preview's both copy `webhook_subscriptions` and `webhook_deliveries` as they are. A delivery is
 * addressed and signed by what is stored on the subscription, not by this app's configuration, so
 * without this check a fork posts its own test events — and production's still-pending outbox rows,
 * a second time — to production's Discord channels, signed with production's secret, which the bot
 * accepts.
 *
 * Production delivers to every subscription. Anywhere else, a subscription is deliverable only if
 * this app could have created it: its callback is under the configured `SEAM_DISCORD_URL`, and it
 * carries the configured `SEAM_WEBHOOK_SHARED_SECRET`. With either unset, nothing is. The secret
 * half covers a worktree pointed at the real Worker with a secret of its own: the copied
 * subscriptions then match on URL, but not on secret.
 *
 * What it cannot cover is a fork configured with production's Worker URL *and* production's
 * secret. The Worker checks every delivery against one secret, so that is the only configuration
 * in which a fork's own Discord posts are accepted, and in it a copied subscription is
 * indistinguishable from one the fork created. `documentation/configuration.md` rules that
 * configuration out.
 *
 * The check is in the app rather than in each script that forks a database, so it also covers the
 * fork paths nobody has written yet.
 */
object WebhookDeliveryScope {

    fun allows(
        env: Env,
        seamDiscordUrl: String?,
        sharedSecret: String?,
        callbackUrl: String,
        secret: String,
    ): Boolean {
        if (env == Production) return true
        if (seamDiscordUrl.isNullOrBlank() || sharedSecret.isNullOrBlank()) return false
        return isUnder(seamDiscordUrl, callbackUrl) &&
            MessageDigest.isEqual(secret.toByteArray(), sharedSecret.toByteArray())
    }

    /**
     * Whether [callbackUrl] is a path below [base]. The trailing slash keeps `https://bot.dev` from
     * admitting `https://bot.dev.example.com`.
     */
    fun isUnder(base: String, callbackUrl: String): Boolean = callbackUrl.startsWith(base.trimEnd('/') + "/")

    fun allowsFromConfig(delivery: DueDelivery): Boolean = allows(
        env = AppConfig.env,
        seamDiscordUrl = AppConfig.seamDiscordUrl,
        sharedSecret = AppConfig.webhookSharedSecret,
        callbackUrl = delivery.callbackUrl,
        secret = delivery.secret,
    )
}
