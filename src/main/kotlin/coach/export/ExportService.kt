package coach.export

import coach.model.TrainingPlan
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ExportService {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

    private const val DEFAULT_BASENAME = "training-plan"
    private val DEFAULT_OUTPUT_DIR: Path = Paths.get("output")

    /**
     * High-level helper:
     * - Ensures [outputDir] exists
     * - Writes both JSON and Markdown
     * - Uses timestamped filenames:
     *   [YYYY]-[MM]-[DD]-[HHmmss]-[baseName].json / .md
     *
     * Returns the two paths as (jsonPath, markdownPath).
     */
    fun export(
        plan: TrainingPlan,
        outputDir: Path = DEFAULT_OUTPUT_DIR,
        baseName: String = DEFAULT_BASENAME
    ): Pair<Path, Path> {
        ensureDirectory(outputDir)

        val jsonPath = buildTimestampedPath(outputDir, baseName, "json")
        val markdownPath = buildTimestampedPath(outputDir, baseName, "md")

        writeJson(plan, jsonPath)
        writeMarkdown(plan, markdownPath)

        return jsonPath to markdownPath
    }

    /**
     * Low-level JSON writer (kept for compatibility).
     * If the parent directory of [path] does not exist, it is created.
     */
    fun writeJson(plan: TrainingPlan, path: Path) {
        ensureParentDirectory(path)
        val content = json.encodeToString(plan)
        Files.writeString(path, content)
    }

    /**
     * Low-level Markdown writer (kept for compatibility).
     * If the parent directory of [path] does not exist, it is created.
     */
    fun writeMarkdown(plan: TrainingPlan, path: Path) {
        ensureParentDirectory(path)
        val content = MarkdownRenderer.render(plan)
        Files.writeString(path, content)
    }

    // --- Internals ---------------------------------------------------------

    private fun ensureDirectory(dir: Path) {
        if (Files.notExists(dir)) {
            Files.createDirectories(dir)
        }
    }

    private fun ensureParentDirectory(path: Path) {
        val parent = path.parent ?: return
        if (Files.notExists(parent)) {
            Files.createDirectories(parent)
        }
    }

    private fun buildTimestampedPath(
        outputDir: Path,
        baseName: String,
        extension: String
    ): Path {
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val filename = "$timestamp-$baseName.$extension"
        return outputDir.resolve(filename)
    }
}