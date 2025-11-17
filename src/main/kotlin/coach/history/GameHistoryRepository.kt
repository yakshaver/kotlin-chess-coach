package coach.history

import coach.model.GameBatch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Simple repository that maintains a growing history of all fetched games
 * in a single JSON file under output/.
 *
 * Format: it stores a GameBatch with the same structure as the latest fetch,
 * but with `games` being the union of all previously seen and newly fetched games.
 */
object GameHistoryRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val outputDir: Path = Paths.get("output")
    private val historyPath: Path = outputDir.resolve("lichess-games-history.json")

    private val backupTimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

    /**
     * Append the games from [newBatch] into the persistent history file.
     *
     * - If the file does not exist, it creates it with [newBatch].
     * - If it exists, it merges existing.games + newBatch.games, de-duplicates,
     *   and writes the merged batch back.
     *
     * Returns the merged GameBatch for convenience.
     */
    fun appendAndSave(newBatch: GameBatch): GameBatch {
        ensureOutputDir()

        val merged = when {
            Files.exists(historyPath) -> {
                val existing = loadHistory()
                mergeBatches(existing, newBatch)
            }
            else -> newBatch
        }

        // Optional: keep a timestamped backup each time we overwrite
        backupCurrentHistoryIfExists()

        val content = json.encodeToString(merged)
        Files.writeString(historyPath, content)

        return merged
    }

    /**
     * Load the current history, or null if it doesn’t exist.
     */
    @Suppress("unused")
    fun loadHistoryOrNull(): GameBatch? =
        if (Files.exists(historyPath)) {
            val text = Files.readString(historyPath)
            json.decodeFromString<GameBatch>(text)
        } else {
            null
        }

    /**
     * Load the current history, or throw if missing.
     */
    fun loadHistory(): GameBatch {
        val text = Files.readString(historyPath)
        return json.decodeFromString(text)
    }

    // --- Internals ---------------------------------------------------------

    private fun ensureOutputDir() {
        if (Files.notExists(outputDir)) {
            Files.createDirectories(outputDir)
        }
    }

    /**
     * Merge two GameBatch instances:
     * - player / site: takes them from [newBatch] (or you could reconcile if needed)
     * - games: union of existing.games + newBatch.games, de-duplicated by data-class equality
     */
    private fun mergeBatches(existing: GameBatch, newBatch: GameBatch): GameBatch {
        val mergedGames = (existing.games + newBatch.games).distinct()
        return existing.copy(
            player = newBatch.player,
            site = newBatch.site,
            games = mergedGames
        )
    }

    /**
     * Optional: write a timestamped backup of the old history before overwriting it.
     */
    private fun backupCurrentHistoryIfExists() {
        if (!Files.exists(historyPath)) return

        val ts = LocalDateTime.now().format(backupTimestampFormatter)
        val backupName = "${ts}-lichess-games-history.json"
        val backupPath = outputDir.resolve(backupName)

        Files.copy(historyPath, backupPath)
    }
}