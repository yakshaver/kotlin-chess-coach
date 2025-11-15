package coach.chess

import coach.model.Color
import coach.model.GameBatch
import coach.model.GameSummary
import coach.model.Result
import coach.model.Site
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object PgnParser {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    /**
     * Parse a PGN string containing one or more games (like Lichess exports) into a GameBatch.
     *
     * This is a deliberately simple parser:
     * - Splits the text into game chunks.
     * - Uses regex to read header tags like [White "..."], [Black "..."], [Result "..."], etc.
     * - Does *not* try to interpret moves — it just counts them roughly.
     */
    fun parseBatchPgn(
        pgn: String,
        site: Site,
        player: String
    ): GameBatch {
        val gamesPgn = splitIntoGames(pgn)
        val summaries = gamesPgn.mapIndexed { index, gamePgn ->
            toGameSummary(index, gamePgn, site, player)
        }

        return GameBatch(
            player = player,
            site = site,
            generatedAtUtc = dateFormatter.format(Instant.now()),
            games = summaries
        )
    }

    // --- Internal helpers ---

    private fun splitIntoGames(pgn: String): List<String> {
        // Normalize line endings
        val normalized = pgn.replace("\r\n", "\n")

        // Naive split: a new game starts where we see "[Event"
        val rawParts = normalized
            .split("\n\n[Event")
            .filter { it.isNotBlank() }

        return rawParts.mapIndexed { index, part ->
            val trimmed = part.trim()
            if (index == 0 && trimmed.startsWith("[Event")) {
                trimmed
            } else {
                "[Event\n$trimmed"
            }
        }.filter { it.isNotBlank() }
    }

    private fun toGameSummary(
        index: Int,
        gamePgn: String,
        site: Site,
        player: String
    ): GameSummary {
        val headers = parseHeaders(gamePgn)
        val movesSection = extractMovesSection(gamePgn)

        val white = headers["White"]
        val black = headers["Black"]

        val color = when {
            white.equals(player, ignoreCase = true) -> Color.WHITE
            black.equals(player, ignoreCase = true) -> Color.BLACK
            else -> Color.WHITE // default if username mismatch
        }

        val resultTag = headers["Result"]
        val result = when {
            resultTag == "1-0" && color == Color.WHITE -> Result.WIN
            resultTag == "0-1" && color == Color.BLACK -> Result.WIN
            resultTag == "1/2-1/2" -> Result.DRAW
            resultTag == "1-0" || resultTag == "0-1" -> Result.LOSS
            else -> Result.OTHER
        }

        val openingEco = headers["ECO"]
        val openingName = headers["Opening"]
        val timeControl = headers["TimeControl"] ?: "unknown"

        val yourRating = when (color) {
            Color.WHITE -> headers["WhiteElo"]?.toIntOrNull()
            Color.BLACK -> headers["BlackElo"]?.toIntOrNull()
        }
        val opponentRating = when (color) {
            Color.WHITE -> headers["BlackElo"]?.toIntOrNull()
            Color.BLACK -> headers["WhiteElo"]?.toIntOrNull()
        }

        val movesCount = approximateMovesCount(movesSection)
        val date = headers["Date"] ?: "????.??.??"

        val id = "${site.name.lowercase()}:${index + 1}"

        return GameSummary(
            id = id,
            timeControl = timeControl,
            rated = headers["Event"]?.contains("Rated", ignoreCase = true) == true,
            perfType = null, // could be inferred from time control later
            result = result,
            color = color,
            opponentRating = opponentRating,
            yourRating = yourRating,
            openingEco = openingEco,
            openingName = openingName,
            movesCount = movesCount,
            utcDate = date,
            pgn = gamePgn
        )
    }

    private fun parseHeaders(gamePgn: String): Map<String, String> {
        // Matches lines like: [Key "Value"]
        val regex = Regex("""\[(\w+)\s+"([^"]*)"]""")
        return regex.findAll(gamePgn)
            .associate { match ->
                val key = match.groupValues[1]
                val value = match.groupValues[2]
                key to value
            }
    }

    private fun extractMovesSection(gamePgn: String): String {
        // Moves start after the blank line following the headers
        val parts = gamePgn.split("\n\n", limit = 2)
        return if (parts.size == 2) parts[1].trim() else ""
    }

    private fun approximateMovesCount(moves: String): Int {
        if (moves.isBlank()) return 0
        // Very rough: count move numbers like "1.", "2.", etc.
        val moveNumberRegex = Regex("""\b\d+\.""")
        val moveNumbers = moveNumberRegex.findAll(moves).count()
        // Each move number typically corresponds to 1–2 half-moves; we’ll just double it as an approximation.
        return moveNumbers * 2
    }
}
