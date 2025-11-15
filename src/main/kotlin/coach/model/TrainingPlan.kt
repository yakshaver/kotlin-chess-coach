package coach.model

import kotlinx.serialization.Serializable

@Serializable
data class TrainingPlan(
    val planVersion: String,
    val targetRating: Int,
    val horizonWeeks: Int,
    val summary: String,
    val focusAreas: List<String>,
    val dailySchedule: List<DayPlan>,
    val notes: String
)

@Serializable
data class DayPlan(
    val dayIndex: Int,
    val label: String,
    val tasks: List<Task>
)

@Serializable
data class Task(
    val type: TaskType,
    val description: String,
    val minutes: Int
)

@Serializable
enum class TaskType {
    GAME, TACTICS, STUDY, REVIEW, ENDGAME, OPENING, OTHER
}
