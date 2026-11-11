package app.mcorg.presentation.templated.legal

import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.security.OAUTH_STATE_COOKIE
import app.mcorg.presentation.templated.dsl.SeamOwner
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.header
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.ul

/**
 * What Seam stores, who sees it and how to delete it (MCO-662).
 *
 * Every statement here was checked against the code when it was written, and each section names
 * where. A change to what is stored, sent or kept is a change to this page, in the same PR. The
 * cookie names are read from their constants; `DeleteAccountIT` pins what deleting an account
 * removes. The rest is prose, so the comment above each section names the code it describes.
 */
const val PRIVACY_LAST_UPDATED = "11 November 2026"

fun privacyPage(): String = pageShell(pageTitle = "Privacy — Seam") {
    header("landing-brand-bar") {
        a(classes = "landing-brand-bar__logo") {
            href = "/"
            +"Seam"
        }
    }
    main {
        container {
            article("legal-page") {
                h1("legal-page__title") { +"Privacy" }
                p("legal-page__updated") { +"Last updated $PRIVACY_LAST_UPDATED" }

                shortVersion()
                signIn()
                theMod()
                whoSees()
                whereItLives()
                cookies()
                logs()
                deleting()
                contact()
            }
        }
    }
}

private fun FlowContent.legalSection(title: String, block: FlowContent.() -> Unit) {
    section("legal-page__section") {
        h2("legal-page__heading") { +title }
        block()
    }
}

private fun FlowContent.shortVersion() = legalSection("The short version") {
    ul {
        li { +"Seam knows your Minecraft username and UUID. It never sees your email address or your Microsoft password." }
        li { +"What you put in a world — projects, tasks, tagged chests — is visible to that world's members, and to nobody else." }
        li { +"No adverts, no analytics, no tracking. Seam is a free hobby project and sells nothing." }
        li { +"You can delete your account from your profile page at any time." }
    }
}

// MinecraftSignInPipeline, CreateUserIfNotExistsStep, UpdateLastSignInStep, V2_53_0 (email dropped).
private fun FlowContent.signIn() = legalSection("When you sign in") {
    p {
        +"You sign in with your Microsoft account. Microsoft, Xbox Live and Minecraft's own services confirm who you are and hand Seam your "
        +"Minecraft profile. Seam keeps:"
    }
    ul {
        li { +"your Minecraft UUID and username, and the name you use inside each world (your username, unless a world admin changes it)" }
        li { +"when you first signed in and when you last did" }
        li { +"which worlds you belong to and your role in each" }
    }
    p {
        +"Seam does not ask Microsoft for your email address and does not store any Microsoft, Xbox or Minecraft token. "
        +"Those are used once, during sign-in, and thrown away."
    }
}

// ApiDtos.kt (the mod's wire contract), V2_67_0 / V2_69_0 / V2_68_0, ContainerSweep.kt.
private fun FlowContent.theMod() = legalSection("The Seam Notebook mod") {
    p { +"If you install the mod and link it to your account, it sends Seam:" }
    ul {
        li { +"the progress you record on a project's resources, and tasks you tick off" }
        li { +"the chests and other containers you tag: their dimension, coordinates, kind and which project they belong to" }
    }
    p {
        +"If a server runs the mod's reporter, the server reads the tagged containers and sends Seam how many of each item a project "
        +"needs is in them. It sends nothing about untagged containers, other blocks, players' inventories or chat."
    }
    p {
        +"Linking the mod creates a token that is stored on your computer, in the game folder. Seam keeps only a fingerprint of it, "
        +"so a copy of Seam's database cannot be used to act as you. You can unlink from the mod at any time."
    }
}

// RolePlugins.kt (WorldParticipantPlugin), IdeaAccess.kt, GetManagedUsersStep.kt.
private fun FlowContent.whoSees() = legalSection("Who can see what") {
    ul {
        li { +"A world's projects, resources, tasks and tagged containers are visible to its members only." }
        li {
            +"An idea you publish to the idea bank is visible to everyone signed in to Seam. "
            +"Ideas you have not published are visible to you only."
        }
        li {
            +"If a world admin connects a Discord channel, Seam posts updates about the world there: project and task names, "
            +"resource progress, and the in-world name of whoever made the change. Anyone who can read that channel sees them."
        }
        li {
            +"Seam's operator can see the list of accounts (username, when they joined, when they last signed in) "
            +"to keep the service running and deal with abuse."
        }
    }
    p { +"Nothing in Seam is visible to someone who is not signed in, apart from this page." }
}

// fly.toml (region arn), configuration.md, rate-limiting.md, WebhookDeliveryPoller.kt, Layout.kt (htmx).
private fun FlowContent.whereItLives() = legalSection("Who else handles it") {
    p { +"Seam runs on a few services that hold or pass on data for it:" }
    ul {
        li { +"Fly.io runs the app, in Stockholm." }
        li { +"Neon hosts the database." }
        li { +"Cloudflare sits in front of seam.gg and app.seam.gg, and runs the small service that posts to Discord." }
        li { +"Discord, only for a world whose admin has connected a channel." }
        li { +"Microsoft, Xbox Live and Minecraft's services, during sign-in." }
        li { +"jsDelivr serves one script the app's pages load, so your browser asks it for that file." }
    }
    p { +"Seam does not sell or share data with anyone else." }
}

// AuthConsts.kt, AddCookieStep.kt, OAuthState.kt.
private fun FlowContent.cookies() = legalSection("Cookies") {
    p { +"Two, both needed to sign in. Neither is used for tracking." }
    ul {
        li {
            code { +AUTH_COOKIE }
            +" keeps you signed in. It expires after 8 hours."
        }
        li {
            code { +OAUTH_STATE_COOKIE }
            +" protects the sign-in itself from forgery. It lasts 15 minutes and is removed once you are signed in."
        }
    }
}

// documentation/logging.md, Monitoring.kt, EdgeOriginGate.kt, WebhookStore.kt (prune).
private fun FlowContent.logs() = legalSection("Logs") {
    p {
        +"The app's logs record which pages were requested and, around sign-in, your Minecraft username and UUID. "
        +"They do not record your email address, what you type into Seam, or your IP address."
    }
    p {
        +"Your IP address is used, in memory only, to stop one address from flooding the service. It is not written to the database."
    }
    p {
        +"A copy of each Discord update is kept for 7 days after it is delivered, or 30 days if delivery failed, so a failed one can be retried."
    }
}

// DeleteAccountPipeline.kt and every FK onto users; DeleteWorldPipeline.kt.
private fun FlowContent.deleting() = legalSection("Deleting your data") {
    p {
        +"Deleting your account (Profile → Delete account) immediately removes your Minecraft profile, your memberships, your ideas "
        +"and comments, your drafts, your mod links, and every world you are the only member of."
    }
    p {
        +"A world that has other members is kept for them, with what you made in it: projects, tasks and tagged containers. "
        +"Your account is no longer linked to any of it. Copies of Discord updates naming you are gone within 30 days; "
        +"updates already posted to a Discord channel stay in Discord."
    }
    p {
        +"Deleting a world (world settings, owner only) removes everything in it: projects, tasks, tagged containers and their readings, "
        +"reporter tokens and Discord connections."
    }
    // Neon's restore window on the project's plan, per the owner (MCO-662). A plan change changes this line.
    p {
        +"The database can be restored to any point in the last 7 days, so deleted data is gone for good 7 days after you delete it."
    }
}

private fun FlowContent.contact() = legalSection("Contact") {
    p {
        +"Seam is made and run by ${SeamOwner.NAME}. For anything about your data, including a request to delete it if you can no longer sign in, write to "
        a(href = "mailto:${SeamOwner.CONTACT_EMAIL}") { +SeamOwner.CONTACT_EMAIL }
        +"."
    }
    div("legal-page__note") {
        p { +"If you are under 18, it is a good idea to show this page to a parent or guardian. They can write to the same address." }
    }
}
