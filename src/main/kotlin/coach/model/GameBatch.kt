package coach.model

import kotlinx.serialization.Serializable

/**
 * A batch of games for a single player from a single source (e.g. Lichess).
 *
 * This is the main structure we:
 * - Serialize to JSON and send to OpenAI
 * - Persist/merge in GameHistoryRepository
 */
@Serializable
data class GameBatch(
    /** The primary player (your Lichess username). */
    val player: String,

    /** Origin site/platform, e.g. "lichess.org". */
    val site: String = "lichess.org",

    /** List of games, most recent first (convention, not enforced). */
    val games: List<ChessGame>
)

/**
 * A single chess game with just enough info for training analysis.
 *
 * This is deliberately compact and stable so it’s safe to send to OpenAI
 * and to store long-term in history JSON.
 */
@Serializable
data class ChessGame(
    /** Unique game identifier on the platform (e.g. Lichess game ID). */
    val id: String,

    /** Whether this was a rated game. */
    val rated: Boolean,

    /** Time control string, e.g. "10+5". */
    val timeControl: String,

    /** Speed category, e.g. "rapid", "blitz", "classical". */
    val speed: String,

    /** Color you played in this game. */
    val color: PlayerColor,

    /** Your result from your perspective. */
    val result: GameResult,

    /** Opponent username (on the same platform). */
    val opponent: String,

    /** Your rating at the start of the game, if known. */
    val playerRating: Int? = null,

    /** Opponent rating at the start of the game, if known. */
    val opponentRating: Int? = null,

    /**
     * ECO code or short opening name if available,
     * e.g. "C50" or "Italian Game".
     */
    val opening: String? = null,

    /**
     * Raw PGN string for the game.
     * This lets OpenAI (or future tools) inspect moves if needed.
     */
    val pgn: String,

    /**
     * Optional ISO-8601 timestamp string for when the game started,
     * e.g. "2025-11-17T14:25:00Z".
     */
    val startedAt: String? = null
)

/**
 * Color from your perspective.
 */
@Serializable
enum class PlayerColor {
    WHITE,
    BLACK
}

/**
 * Result from your perspective.
 */
@Serializable
enum class GameResult {
    WIN,
    LOSS,
    DRAW,
    UNKNOWN
}