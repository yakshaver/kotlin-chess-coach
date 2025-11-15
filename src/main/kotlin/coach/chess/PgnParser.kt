package coach.chess

import coach.model.*
import com.github.bhlangonijr.chesslib.game.Game
import com.github.bhlangonijr.chesslib.pgn.PgnHolder
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object PgnParser {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    /**
     * Parse a PGN string containing one or more games into a GameBatch.
     */
    fun parseBatchPgn(
        pgn: String,
        site: Site,
        player: String
    ): GameBatch {
        val tmpFile = Files.createTempFile("lichess-games", ".pgn").toFile()
        tmpFile.writeText(pgn)

        val holder = PgnHolder(tmpFile.absolutePath)
        holder.loadGames()

        val games = holder.games.map { g -> toGameSummary(g, site, player) }

        tmpFile.delete()

        return GameBatch(
            player = player,
            site = site,
            generatedAtUtc = dateFormatter.format(Instant.now()),
            games = games
        )
    }

    private fun toGameSummary(
        game: Game,
        site: Site,
        player: String
    ): GameSummary {
        val headers = game.gameHeaders

        val white = headers.playerWhite
        val black = headers.playerBlack
        val color = if (white.equals(player, ignoreCase = true)) Color.WHITE else Color.BLACK

        val resultString = headers.result.result
        val result = when {
            resultString == "1-0" && color == Color.WHITE -> Result.WIN
            resultString == "0-1" && color == Color.BLACK -> Result.WIN
            resultString == "1/2-1/2" -> Result.DRAW
            else -> Result.LOSS
        }

        val openingEco = headers.eco
        val openingName = headers.opening
        val timeControl = headers.timeControl ?: "unknown"
        val movesCount = game.halfMoves.size

        val yourRating =
            (if (color == Color.WHITE) headers.whiteElo else headers.blackElo)?.toIntOrNull()
        val oppRating =
            (if (color == Color.WHITE) headers.blackElo else headers.whiteElo)?.toIntOrNull()

        val id = "${site.name.lowercase()}:${headers.site ?: game.id ?: "game-${headers.date}-${headers.round}"}"

        val date = headers.date ?: "????.??.??"

        return GameSummary(
            id = id,
            timeControl = timeControl,
            rated = headers.event?.contains("Rated", ignoreCase = true) == true,
            perfType = null, // could be inferred from timeControl later
            result = result,
            color = color,
            opponentRating = oppRating,
            yourRating = yourRating,
            openingEco = openingEco,
            openingName = openingName,
            movesCount = movesCount,
            utcDate = date,
            pgn = game.toString()
        )
    }
}
