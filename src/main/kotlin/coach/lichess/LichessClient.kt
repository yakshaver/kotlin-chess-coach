package coach.lichess

import arrow.core.Either
import arrow.core.raise.either
import coach.http.HttpClientFactory
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

class LichessClient(
    private val token: String
) {
    private val client = HttpClientFactory.default

    /**
     * Fetch last [max] games for [username] in PGN format.
     */
    suspend fun fetchLastGamesPgn(
        username: String,
        max: Int = 30
    ): Either<Throwable, String> = either {
        val url =
            "https://lichess.org/api/games/user/$username?max=$max&moves=true&tags=true&clocks=false&evals=false&opening=true"
        client.get(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<String>()
    }
}
