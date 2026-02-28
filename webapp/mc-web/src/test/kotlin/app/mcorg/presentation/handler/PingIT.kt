package app.mcorg.presentation.handler

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import org.junit.jupiter.api.Test
import io.ktor.server.testing.*

class PingIT {
    @Test
    fun testPing() = testApplication {
        routing {
            get("/ping") {
                call.respond(HttpStatusCode.OK, "OK")
            }
        }

        val response = this.client.get("/ping")
        assert(response.status == HttpStatusCode.OK)
        assert(response.bodyAsText() == "OK")
    }
}