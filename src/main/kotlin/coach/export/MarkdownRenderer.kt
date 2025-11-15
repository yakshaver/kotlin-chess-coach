package coach.export

import coach.model.TrainingPlan
import coach.model.TaskType

object MarkdownRenderer {

    fun render(plan: TrainingPlan): String = buildString {
        appendLine("# Chess Training Plan (v${plan.planVersion})")
        appendLine()
        appendLine("- Target rating: ${plan.targetRating}")
        appendLine("- Horizon: ${plan.horizonWeeks} weeks")
        appendLine()
        appendLine("## Summary")
        appendLine(plan.summary)
        appendLine()
        appendLine("## Focus Areas")
        plan.focusAreas.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Daily Schedule")
        plan.dailySchedule.forEach { day ->
            appendLine()
            appendLine("### ${day.label} (Day ${day.dayIndex})")
            day.tasks.forEach { t ->
                val typeLabel = when (t.type) {
                    TaskType.GAME -> "Game"
                    TaskType.TACTICS -> "Tactics"
                    TaskType.STUDY -> "Study"
                    TaskType.REVIEW -> "Review"
                    TaskType.ENDGAME -> "Endgame"
                    TaskType.OPENING -> "Opening"
                    TaskType.OTHER -> "Other"
                }
                appendLine("- **$typeLabel** – ~${t.minutes} min: ${t.description}")
            }
        }
        plan.notes?.let {
            appendLine()
            appendLine("## Notes")
            appendLine(it)
        }
    }
}
