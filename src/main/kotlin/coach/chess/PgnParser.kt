package coach.chess

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import coach.model.GameBatch
import coach.model.ChessGame
import coach.model.GameResult
import coach.model.PlayerColor
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import com.github.bhlangonijr.chesslib.pgn.PgnHolder
import com.github.bhlangonijr.chesslib.game.Game as LibGame

object PgnParser {

    /**
     * Pure top-level API: build a GameBatch from raw PGN.
     * Internally uses kchesslib/chesslib to parse PGN robustly.
     */
    fun buildGameBatch(
        player: String,
        site: String = "lichess.org",
        rawPgn: String
    ): GameBatch {
        val libGames = parseWithKChessLib(rawPgn)
        val originals = splitIntoGamesText(rawPgn)
        val games = libGames.mapIndexed { idx, libGame ->
            val pgnText = originals.getOrNull(idx) ?: runCatching { libGame.toPgn(false, false) }.getOrDefault("")
            toChessGame(idx, libGame, pgnText, player, site)
        }
        return GameBatch(player = player, site = site, games = games)
    }

    // --- Parsing (side-effect at the edge: temp file I/O) -------------------

    private fun parseWithKChessLib(rawPgn: String): List<LibGame> {
        val temp = Files.createTempFile("pgn_batch_", ".pgn").toFile()
        try {
            Files.writeString(temp.toPath(), rawPgn, StandardOpenOption.TRUNCATE_EXISTING)
            val holder = PgnHolder(temp.absolutePath)
            holder.loadPgn()
            return holder.games.toList()
        } finally {
            runCatching { temp.delete() }
        }
    }

    private fun splitIntoGamesText(pgn: String): List<String> =
        pgn.replace("\r\n", "\n")
            .split("\n\n[Event")
            .filter { it.isNotBlank() }
            .mapIndexed { index, part ->
                val trimmed = part.trim()
                if (index == 0 && trimmed.startsWith("[Event")) trimmed else "[Event\n$trimmed"
            }
            .filter { it.isNotBlank() }

    // --- Pure mapping helpers ----------------------------------------------

    private fun toChessGame(index: Int, @Suppress("UNUSED_PARAMETER") game: LibGame, pgnText: String, player: String, site: String): ChessGame {
        val headers: Map<String, String> = parseHeadersFromText(pgnText)

        val white = headers["White"]
        val black = headers["Black"]

        val color = when {
            white.equals(player, ignoreCase = true) -> PlayerColor.WHITE
            black.equals(player, ignoreCase = true) -> PlayerColor.BLACK
            else -> PlayerColor.WHITE
        }

        val result = resolveResult(headers["Result"], color)

        val opening = headers["ECO"] ?: headers["Opening"]

        val rated = headers["Event"]?.contains("Rated", ignoreCase = true) == true

        val timeControlRaw = headers["TimeControl"]
        val timeControl = normalizeTimeControl(timeControlRaw)

        val speed = inferSpeed(headers["Event"], timeControl)

        val playerRating = when (color) {
            PlayerColor.WHITE -> headers["WhiteElo"]?.toIntOrNull()
            PlayerColor.BLACK -> headers["BlackElo"]?.toIntOrNull()
        }
        val opponentRating = when (color) {
            PlayerColor.WHITE -> headers["BlackElo"]?.toIntOrNull()
            PlayerColor.BLACK -> headers["WhiteElo"]?.toIntOrNull()
        }

        val opponent = when (color) {
            PlayerColor.WHITE -> black ?: "unknown"
            PlayerColor.BLACK -> white ?: "unknown"
        }

        val startedAt = buildUtcDateTime(headers).getOrNull()

        val id = extractLichessId(headers).getOrElse { "${site}:${index + 1}" }

        return ChessGame(
            id = id,
            rated = rated,
            timeControl = timeControl ?: "unknown",
            speed = speed ?: "unknown",
            color = color,
            result = result,
            opponent = opponent,
            playerRating = playerRating,
            opponentRating = opponentRating,
            opening = opening,
            pgn = pgnText,
            startedAt = startedAt
        )
    }

    private fun parseHeadersFromText(text: String): Map<String, String> {
        val regex = Regex("""\[(\w+)\s+"([^"]*)"]""")
        return regex.findAll(text).associate { m -> m.groupValues[1] to m.groupValues[2] }
    }

    private fun resolveResult(resultTag: String?, color: PlayerColor): GameResult = when (resultTag) {
        "1-0" -> if (color == PlayerColor.WHITE) GameResult.WIN else GameResult.LOSS
        "0-1" -> if (color == PlayerColor.BLACK) GameResult.WIN else GameResult.LOSS
        "1/2-1/2" -> GameResult.DRAW
        "*" -> GameResult.UNKNOWN
        else -> GameResult.UNKNOWN
    }

    private fun normalizeTimeControl(tc: String?): String? {
        if (tc.isNullOrBlank()) return null
        val parts = tc.split("+")
        val base = parts.getOrNull(0)?.toIntOrNull() ?: return tc
        val inc = parts.getOrNull(1)
        return if (base % 60 == 0) {
            val minutes = base / 60
            if (inc != null) "$minutes+${inc}" else "$minutes+0"
        } else tc
    }

    private fun inferSpeed(event: String?, timeControl: String?): String? {
        val e = event?.lowercase()
        if (e != null) {
            when {
                "bullet" in e -> return "bullet"
                "blitz" in e -> return "blitz"
                "rapid" in e -> return "rapid"
                "classical" in e -> return "classical"
            }
        }
        val minutes = timeControl?.substringBefore('+')?.toIntOrNull()
        return when {
            minutes == null -> null
            minutes < 3 -> "bullet"
            minutes <= 8 -> "blitz"
            minutes <= 25 -> "rapid"
            else -> "classical"
        }
    }

    private fun buildUtcDateTime(headers: Map<String, String>): Option<String> {
        val (d, t) = headers["UTCDate"] to headers["UTCTime"]
        return if (!d.isNullOrBlank() && !t.isNullOrBlank()) {
            val yyyyMmDd = d.replace('.', '-')
            val time = t.take(8)
            "${yyyyMmDd}T${time}Z".some()
        } else none()
    }

    private fun extractLichessId(headers: Map<String, String>): Result<String> =
        runCatching {
            val link = headers["Site"] ?: headers["Link"]
            if (link.isNullOrBlank()) error("no link")
            link.trim().removeSuffix("/").substringAfterLast('/')
        }
}
