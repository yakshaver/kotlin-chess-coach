# kotlin-chess-coach

A small Kotlin project that automates chess training for an adult improver using their real games, the Lichess API, and the OpenAI ChatGPT API.

**Goal:**  
Given your recent rapid games and a fixed training budget (e.g. **30 minutes per day**), the app:

1. Fetches your games from Lichess (Chess.com later).
2. Parses them into a structured `GameBatch`.
3. Sends that batch to the ChatGPT API with a structured-output schema.
4. Receives a `TrainingPlan` JSON describing a **2-week, 30-min/day training schedule** tailored to your weaknesses.
5. Writes out `training-plan.json` and a human-friendly `training-plan.md`.

You then just follow the plan.

---

## High-Level Architecture

```text
[Lichess]  -->  [Kotlin ETL]  -->  [GameBatch JSON]
                          \
                           -->  [OpenAI ChatGPT API]  -->  [TrainingPlan JSON]
                                                                   |
                                                                   --> training-plan.md
```

### Components

- **Source adapters**
  - `LichessClient`: fetch recent games as PGN for a user.

- **PGN parsing**
  - `PgnParser`: uses kchesslib to:
    - Parse PGN.
    - Extract metadata (result, ratings, ECO, opening name, moves count).
    - Build a `GameBatch` domain object.

- **Domain models**
  - `GameBatch`: bundle of `GameSummary` records (input to OpenAI).
  - `TrainingPlan`: structured JSON training plan from OpenAI.

- **OpenAI integration**
  - `OpenAIClient`: sends system + user prompts with `GameBatch` JSON and a `TrainingPlan` JSON Schema as `response_format`.
  - Receives, validates, and deserializes `TrainingPlan`.

- **Export**
  - `ExportService`: writes:
    - `training-plan.json` – raw structured output.
    - `training-plan.md` – human-friendly Markdown summary.

---

## Data Model Overview

### `GameBatch` (input to ChatGPT)

```kotlin
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

@Serializable enum class Result { WIN, LOSS, DRAW, OTHER }
@Serializable enum class Color { WHITE, BLACK }
```

### `TrainingPlan` (output from ChatGPT)

```kotlin
@Serializable
data class TrainingPlan(
    val planVersion: String,        // e.g. "v1.0"
    val targetRating: Int,          // e.g. 1500
    val horizonWeeks: Int,          // usually 2
    val summary: String,            // human-readable overview
    val focusAreas: List<String>,   // ["tactics: forks", "king safety", ...]
    val dailySchedule: List<DayPlan>,
    val notes: String? = null
)

@Serializable
data class DayPlan(
    val dayIndex: Int,              // 1..7
    val label: String,              // "Day 1", "Monday", etc.
    val tasks: List<Task>
)

@Serializable
data class Task(
    val type: TaskType,             // GAME, TACTICS, STUDY, REVIEW, ENDGAME, OPENING, OTHER
    val description: String,
    val minutes: Int
)

@Serializable
enum class TaskType {
    GAME, TACTICS, STUDY, REVIEW, ENDGAME, OPENING, OTHER
}
```

---

## Project Structure

```text
kotlin-chess-coach/
  README.md
  build.gradle.kts
  settings.gradle.kts
  src/
    main/
      kotlin/
        coach/
          App.kt                   // CLI entry point
          http/
            HttpClientFactory.kt
          lichess/
            LichessClient.kt
          chess/
            PgnParser.kt
          model/
            GameModels.kt          // GameBatch, GameSummary, enums
            TrainingPlan.kt        // TrainingPlan, DayPlan, Task, enums
          openai/
            OpenAIClient.kt
          export/
            ExportService.kt
            MarkdownRenderer.kt
    test/
      kotlin/
        coach/
          PlaceholderTest.kt
```

---

## Setup

1. **Create API tokens**
   - Lichess:
     - Create a personal access token with `Read games` scope.
   - OpenAI:
     - Create an API key from your OpenAI account (with access to GPT-5.1 or GPT-5-mini family).

2. **Set environment variables**

   ```bash
   export LICHESS_USER="your_lichess_username"
   export LICHESS_TOKEN="your_lichess_token"
   export OPENAI_API_KEY="sk-..."
   ```

3. **Build & run (from project root)**

   ```bash
   ./gradlew run
   ```

   This will:
   - Fetch your last N games from Lichess.
   - Build a `GameBatch`.
   - Call the OpenAI ChatGPT API for a `TrainingPlan`.
   - Write `training-plan.json` and `training-plan.md` in the project root.

Open the Markdown file and follow the daily schedule (e.g., a 2-week, 30-min/day training plan to push you toward ~1500 rapid).
