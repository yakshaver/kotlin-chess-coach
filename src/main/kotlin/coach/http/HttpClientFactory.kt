package coach.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {
    val default: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = false
                    }
                )
            }

            // Add sensible timeouts for OpenAI / Lichess calls
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000   // total time for request+response
                connectTimeoutMillis = 15_000   // time to establish TCP connection
                socketTimeoutMillis  = 60_000   // inactivity on the socket
            }
        }
    }
}