package coach.model

import kotlinx.serialization.Serializable

@Serializable
data class GameBatch(
    val player: String,
    val site: Site,
    val generatedAtUtc: String,
    val games: List<GameSummary>
)

@Serializable
enum class Site { LICHESS, CHESS_COM }

@Serializable
data class GameSummary(
    val id: String,
    val timeControl: String,
    val rated: Boolean,
    val perfType: String?,    // e.g. "rapid"
    val result: Result,       // WIN / LOSS / DRAW / OTHER
    val color: Color,         // WHITE / BLACK
    val opponentRating: Int?,
    val yourRating: Int?,
    val openingEco: String?,
    val openingName: String?,
    val movesCount: Int,
    val utcDate: String,
    val pgn: String
)

@Serializable
enum class Result { WIN, LOSS, DRAW, OTHER }

@Serializable
enum class Color { WHITE, BLACK }
