package coach.export

import coach.model.TrainingPlan
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

object ExportService {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun writeJson(plan: TrainingPlan, path: Path) {
        val content = json.encodeToString(plan)
        Files.writeString(path, content)
    }

    fun writeMarkdown(plan: TrainingPlan, path: Path) {
        val content = MarkdownRenderer.render(plan)
        Files.writeString(path, content)
    }
}
