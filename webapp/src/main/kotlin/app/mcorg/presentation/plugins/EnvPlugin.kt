package app.mcorg.presentation.plugins

import app.mcorg.presentation.utils.*
import io.ktor.server.application.*

val EnvPlugin = createRouteScopedPlugin("EnvPlugin") {
    onCall {
        System.getenv("DB_URL")?.let { dbUrl -> it.setDBUrl(dbUrl) }
        System.getenv("DB_USER")?.let { dbUser -> it.setDBUser(dbUser) }
        System.getenv("DB_PASSWORD")?.let { dbPassword -> it.setDBPassword(dbPassword) }
        System.getenv("MICROSOFT_CLIENT_ID")?.let { clientId -> it.setMicrosoftClientId(clientId) }
        System.getenv("MICROSOFT_CLIENT_SECRET")?.let { clientSecret -> it.setMicrosoftClientSecret(clientSecret) }
        System.getenv("ENV")?.let { env -> it.setEnvironment(env) }
        System.getenv("COOKIE_HOST")?.let { cookieHost -> it.setCookieHost(cookieHost) }
        System.getenv("SKIP_MICROSOFT_SIGN_IN")?.let { skip -> it.setSkipMicrosoftSignIn(skip) }
    }
}