package coach

import arrow.core.getOrElse
import coach.chess.PgnParser
import coach.export.ExportService
import coach.history.GameHistoryRepository
import coach.lichess.LichessClient
import coach.openai.OpenAIClient
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val username = System.getenv("LICHESS_USER")
        ?: error("Environment variable LICHESS_USER must be set")
    val lichessToken = System.getenv("LICHESS_TOKEN")
        ?: error("Environment variable LICHESS_TOKEN must be set")
    val openaiKey = System.getenv("OPENAI_API_KEY")
        ?: error("Environment variable OPENAI_API_KEY must be set")

    val maxGames = System.getenv("MAX_GAMES")?.toIntOrNull() ?: 30
    val targetRating = System.getenv("TARGET_RATING")?.toIntOrNull() ?: 1500
    val horizonWeeks = System.getenv("HORIZON_WEEKS")?.toIntOrNull() ?: 2

    println("Fetching last $maxGames games for Lichess user: $username")

    val lichessClient = LichessClient(lichessToken)
    val pgn = lichessClient.fetchLastGamesPgn(username, maxGames)
        .getOrElse {
            error("Failed to fetch games from Lichess: ${it.message}")
        }

    println("Parsing PGN into GameBatch...")
    val batch = PgnParser.buildGameBatch(
        player = username,
        site = "lichess.org",
        rawPgn = pgn
    )

    // after you have `batch`:
    println("Appending games to history...")
    val history = GameHistoryRepository.appendAndSave(batch)
    println("History now contains ${history.games.size} games.")

    val currentRating =
        batch.games.mapNotNull { it.playerRating }.lastOrNull()
            ?: System.getenv("CURRENT_RATING")?.toIntOrNull()
            ?: 800

    println("Current inferred rating: $currentRating")
    println("Calling OpenAI to generate training plan...")

    val openAIClient = OpenAIClient(openaiKey)
    val plan = openAIClient.generateTrainingPlan(
        batch = batch,
        currentRating = currentRating,
        targetRating = targetRating,
        horizonWeeks = horizonWeeks
    )

    val (jsonPath, mdPath) = ExportService.export(plan)
    println("Wrote training plan to:")
    println("  $jsonPath")
    println("  $mdPath")
}
