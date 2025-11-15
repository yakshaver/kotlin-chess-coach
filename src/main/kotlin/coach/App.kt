package coach

import coach.chess.PgnParser
import coach.export.ExportService
import coach.lichess.LichessClient
import coach.model.Site
import coach.openai.OpenAIClient
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

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
            it.printStackTrace()
            error("Failed to fetch games from Lichess: ${it.message}")
        }

    println("Parsing PGN into GameBatch...")
    val batch = PgnParser.parseBatchPgn(pgn, Site.LICHESS, username)

    val currentRating =
        batch.games.mapNotNull { it.yourRating }.lastOrNull()
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

    val jsonPath = Paths.get("training-plan.json")
    val mdPath = Paths.get("training-plan.md")

    ExportService.writeJson(plan, jsonPath)
    ExportService.writeMarkdown(plan, mdPath)

    println("Wrote training plan to:")
    println("  JSON: ${jsonPath.toAbsolutePath()}")
    println("  Markdown: ${mdPath.toAbsolutePath()}")
}
