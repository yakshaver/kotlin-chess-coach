# Kotlin Chess Coach – Architecture

## 1. Purpose

This project is a small Kotlin tool that generates **personalized chess training plans** from recent Lichess games.

**Goal:**  
Help an adult improver climb toward ~**1500** as efficiently as possible, with about **30 minutes of structured training per day**.

**Inputs:**

- Recent games from **Lichess** (via API + PGN)
- Current rating (inferred, plus optional explicit value)
- Target rating (default 1500)
- Training horizon in weeks (default 2)

**Outputs:**

- A structured `TrainingPlan` (JSON)
- A human-friendly Markdown rendering of that plan
- Both exported to `output/` with timestamped filenames

---

## 2. High-Level Data Flow

1. **App startup (`App.kt`)**
   - Reads environment variables:
     - `LICHESS_USER`
     - `LICHESS_TOKEN`
     - `OPENAI_API_KEY`
     - (optional) `OPENAI_MODEL`
   - Creates shared `HttpClient` via `HttpClientFactory`.

2. **Fetch games from Lichess (`coach.chess`)**
   - Calls Lichess API for the last N games (currently 30).
   - Parses PGN into an internal `GameBatch` model.

3. **Infer current rating**
   - Uses game metadata (or heuristics) to estimate current rating if not explicit.
   - For now: simple, coarse inference (e.g., ~800 from sample data).

4. **Generate training plan (`coach.openai.OpenAIClient`)**
   - Builds a **JSON schema** for `TrainingPlan` (`trainingPlanSchema()`).
   - Sends a `chat/completions` request to OpenAI with:
     - `model` (e.g., `gpt-4.1-mini` or `gpt-5.1`)
     - `messages` (system + user prompts)
     - `response_format` with strict JSON schema (`type: "json_schema"`, `strict: true`)
   - Receives and deserializes a JSON `TrainingPlan`.

5. **Export results (`coach.export.ExportService`)**
   - Writes the `TrainingPlan` to:
     - `output/YYYY-MM-DD-HHmmss-training-plan.json`
     - `output/YYYY-MM-DD-HHmmss-training-plan.md`
6. **Design & Implementation Principles (Summary)**
   - Favor **functional programming**:
       - Immutability by default.
       - Pure functions for core logic (parsing, mapping, transformations).
       - Side effects (I/O, network) pushed to the edges (e.g., App.kt, HTTP clients).

   - Always accompany new core logic with **unit tests**:
       - Test pure functions directly.
       - Ensure tests are fast and deterministic.
       - Tests must pass.

For detailed coding and testing conventions, see `docs/engineering-guidelines.md`.

---

## 3. Modules and Responsibilities

### 3.1 `coach.App` (entry point)

- Orchestrates the full pipeline:
  - Read env vars.
  - Fetch `GameBatch` from Lichess.
  - Infer current rating.
  - Call `OpenAIClient.generateTrainingPlan`.
  - Call `ExportService.export`.
- Logs key steps for observability.

### 3.2 `coach.http.HttpClientFactory`

```kotlin
object HttpClientFactory {
    val default: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = false
                    }
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis  = 60_000
            }
        }
    }
}
