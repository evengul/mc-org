package app.mcorg.pipeline

import app.mcorg.domain.pipeline.Step
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.Url
import io.ktor.http.cio.Response
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

suspend inline fun <I, E, S> Step<I, E, S>.apiGetForm(path: String, builder: ParametersBuilder.() -> Unit): HttpResponse {
    return useJsonClient {
        get(url = Url(path)) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(Parameters.build {
                builder()
            }))
        }
    }
}

suspend inline fun <I, E, S> Step<I, E, S>.apiGetJson(path: String, accessToken: String? = null): HttpResponse {
    return useJsonClient {
        get(url = Url(path)) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            if (accessToken != null) {
                bearerAuth(accessToken)
            }
        }
    }
}

suspend inline fun <reified B, I, E, S> Step<I, E, S>.apiPostJson(path: String, body: B): HttpResponse {
    return useJsonClient {
        post(url = Url(path)) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(body)
        }
    }
}

suspend inline fun <I, E, S> Step<I, E, S>.useJsonClient(handler: suspend HttpClient.() -> HttpResponse) = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
        }
    }
}.use { it.handler() }