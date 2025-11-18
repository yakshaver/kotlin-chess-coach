### Overview
I reviewed the unpushed change set for `kotlin-chess-coach` against the project’s engineering guidelines, architecture, and pre‑push review checklist. Below is a structured report covering what changed, why it matters, functional style and architecture, model invariants, tests, git hygiene/secrets, code cleanliness, and a final push checklist.

---

### 1) Understand the change
Files changed compared to `main` (from your VCS status):

- Added: `.junie/guidelines.md`
    - What: Project‑specific guidance for using Junie as a pre‑push reviewer and enforcing functional style and hygiene.
    - Why it matters: This codifies expectations and review scope; no runtime impact.

- Added: `src/main/kotlin/coach/model/GameBatch.kt`
    - What: Introduces core domain models `GameBatch`, `ChessGame`, `PlayerColor`, `GameResult` annotated with `@Serializable`.
    - Why it matters: This is the canonical game representation sent to OpenAI and persisted in history. It replaces legacy models and is now used by the parser, exporter pipeline, and history features.

- Modified: `src/main/kotlin/coach/chess/PgnParser.kt`
    - What: New pure entry `buildGameBatch(player, site, rawPgn)`, uses chesslib (`PgnHolder`) to parse PGN robustly, maps to `ChessGame` with helpers for headers, result, time control normalization, speed inference, timestamp, and ID extraction. Keeps raw PGN per game.
    - Why it matters: This directly influences the correctness and stability of `GameBatch` generation, a core input to OpenAI and history. It shifts parsing to a library and keeps mapping pure.

- Added: `src/main/kotlin/coach/history/GameHistoryRepository.kt`
    - What: Maintains and persists cumulative game history in `output/lichess-games-history.json`. Provides `appendAndSave(newBatch)` with merge and timestamped backups.
    - Why it matters: Introduces long‑term history, enabling trend analysis and stable state across runs. Writes to `output/`, which must not be committed.

- Modified: `src/main/kotlin/coach/App.kt`
    - What: Orchestrates pipeline: read env, fetch PGN, build `GameBatch`, append to history, infer current rating, call OpenAI for `TrainingPlan`, export JSON/Markdown.
    - Why it matters: Establishes side‑effect boundaries at the app edge; connects all components.

- Modified: `docs/architecture.md`
    - What: Documents the updated data flow (GameBatch, history, OpenAI schema) and design principles.
    - Why it matters: Keeps documentation aligned with new architecture.

- Modified: `src/main/kotlin/coach/model/GameModels.kt`
    - What: Reduced to a minimal stub noting it’s replaced by `GameBatch/ChessGame`.
    - Why it matters: Minimizes conflicts with older types and clarifies the canonical model.

Related files touched/used by the new flow:
- `src/main/kotlin/coach/openai/OpenAIClient.kt`: Generates `TrainingPlan` using `response_format` JSON schema; consumes `GameBatch` JSON.
- `src/main/kotlin/coach/export/ExportService.kt` and `src/main/kotlin/coach/export/MarkdownRenderer.kt`: Export the plan to `output/` as JSON and Markdown.
- Tests: `src/test/kotlin/coach/chess/PgnParserTest.kt` cover parser behavior.

---

### 2) Functional style and architecture
- Immutability:
    - `GameBatch` and `ChessGame` are immutable data classes with `val` fields. Good.
    - Collections are immutable (`List`). Good.

- Pure functions vs side effects:
    - `PgnParser.buildGameBatch` is pure from the caller’s perspective given a `rawPgn` input.
    - Internally, `parseWithKChessLib` writes a temp file to feed `PgnHolder`. This is an internal side effect. It’s acceptable if the library requires file input; consider a follow‑up to parse from in‑memory PGN if supported to remove this side effect. All mapping helpers are pure.
    - `GameHistoryRepository` performs file I/O and timestamps (side effects) at the “edge,” which is correct for a repository.
    - `App.kt` centralizes external effects: env access, network calls, history write, export write, logging. Good separation.

- Use of Kotlin collections/HOFs:
    - `PgnParser` uses `mapIndexed`, `associate`, `filter`, etc. Good.
    - `GameHistoryRepository.mergeBatches` uses `(existing + new).distinct()`. See the de‑dupe note below.

Concrete improvement suggestions:
- `GameHistoryRepository.mergeBatches` de‑dupe strategy: Using data‑class equality (`distinct()`) may treat the same game as different if mutable fields such as `pgn` or future metadata change (e.g., a later fetch has a corrected opening or rating). Prefer de‑dupe by a stable key: `id` (platform game ID). Example approach: `val merged = (existing.games + new.games).groupBy { it.id }.map { (_, v) -> v.maxBy { it.startedAt ?: "" } }` or establish a merge policy.
- `PgnParser.parseWithKChessLib`: If the library can parse from a stream/string, prefer that to avoid temp file I/O. If not, keep as‑is but document and cover with tests for error handling.

---

### 3) Model invariants
- TrainingPlan vs OpenAI schema:
    - `TrainingPlan` fields: `planVersion: String`, `targetRating: Int`, `horizonWeeks: Int`, `summary: String`, `focusAreas: List<String>`, `dailySchedule: List<DayPlan>`, `notes: String`.
    - `OpenAIClient.trainingPlanSchema()` defines the same properties with matching types and requires all of them, including `notes`. This is in sync. Good.
    - `TaskType` enum values match the schema enum: `GAME, TACTICS, STUDY, REVIEW, ENDGAME, OPENING, OTHER`. Good.

- GameBatch / chess model consistency:
    - `PgnParser` outputs `GameBatch` with `ChessGame` fields consistent with the domain expectations: `id`, `rated`, `timeControl` normalized to `m+inc` when parseable, `speed` inferred, `color` resolved relative to `player`, `result` from perspective, `pgn` retained, `startedAt` ISO 8601 or null.
    - `GameHistoryRepository` persists/loads `GameBatch` with `kotlinx.serialization` using the same model. Good.
    - `OpenAIClient` serializes `GameBatch` to JSON as input to the model. Good.

Potential mismatch to flag:
- `MarkdownRenderer` renders `plan.notes` as optional: it calls `plan.notes?.let { ... }`. However, `TrainingPlan.notes` is non‑nullable and required in the schema. This is a small inconsistency. Either:
    - Make `notes` optional in the schema and model, or
    - Render `notes` unconditionally in the Markdown renderer. Given the current schema and model, the latter is consistent: just always render the “Notes” section.

---

### 4) Tests
Current tests:
- `src/test/kotlin/coach/chess/PgnParserTest.kt` has solid coverage for:
    - Mapping of headers to fields (IDs, ratings, colors, result perspective).
    - Time control normalization (`600+5` -> `10+5`, `180+0` -> `3+0`).
    - Speed inference via event string or by minutes.
    - Defaults when metadata missing (unknowns, nulls).

Gaps and recommended tests:
- History repository:
    - Add tests for `GameHistoryRepository.mergeBatches` covering:
        - De‑dupe by `id` rather than full object equality (once implemented).
        - Preservation of newest metadata when duplicates occur (e.g., prefer record with `startedAt` or with most complete ratings).
        - Stability of `player`/`site` from `newBatch` after merge.
    - Place under `src/test/kotlin/coach/history/GameHistoryRepositoryTest.kt`. Use a temp directory or mock filesystem path injection if possible; otherwise, refactor to allow an override of the output path for tests to keep them isolated and deterministic.

- Markdown rendering:
    - Add `MarkdownRendererTest` verifying that `notes` are always rendered according to current model invariants and that task types and formatting appear as intended.

- OpenAI client:
    - Do not test network calls; if you want minimal coverage, unit test `trainingPlanSchema()` shape by asserting the presence of required keys and enum values. Keep fast and offline.

- Parser edge cases:
    - Additional tests for multi‑game splitting and resilience to weird line endings (`\r\n` vs `\n`). A test already normalizes CRLF; ensure a case is present.

Reminder: Before push, run `./gradlew test` to ensure everything passes.

---

### 5) Git hygiene & secrets
- Tracked generated output:
    - The repository currently contains files under `output/`:
        - `output/2025-11-15-155353-training-plan.json` and `.md`
        - `output/2025-11-17-060738-training-plan.json` and `.md`
        - `output/2025-11-17-072442-training-plan.json` and `.md`
        - `output/lichess-games-history.json`
    - These should not be committed. Remove them from git history and add (or ensure) `output/` is in `.gitignore`.

- Tracked secrets:
    - `env.sh` contains real credentials: `LICHESS_TOKEN` and `OPENAI_API_KEY`. This is a critical security risk.
    - Action items:
        - Remove `env.sh` from version control, rotate both tokens/keys immediately (assume compromised), and ensure secrets are only provided via environment variables or a local untracked `.env`.
        - Update `.gitignore` to exclude any `.env`, `env.sh`, or similar.

- Other artifacts:
    - Ensure build artifacts (`build/`, `.gradle/`), IDE files (`.idea/`, `.DS_Store`) are ignored and not committed.

---

### 6) Code cleanliness
- Unused imports:
    - No obvious unused imports in the changed files you shared. Keep an eye if IDE highlights any in other touched files.

- Debug logging / printlns:
    - `App.kt` uses `println` for progress. That’s acceptable for a CLI tool, but consider a simple logger if you want adjustable verbosity later.

- Dead code:
    - `coach/model/GameModels.kt` is a minimal stub. If it’s no longer needed by any references, consider removing it entirely. If you keep it temporarily, add a clear comment with a deprecation note and a plan to delete.

- Naming/style:
    - Consistent with Kotlin conventions. Functional style is maintained.

- Minor nits:
    - `MarkdownRenderer` treats `notes` as optional, which mismatches the model/schema. Fix per the invariant note above.
    - `GameHistoryRepository` backups name pattern: they’re written to `output/` and will accumulate. That’s fine locally, but ensure they remain untracked.

---

### 7) Final pre‑push summary and checklist
- Changed files summary:
    - Added `GameBatch` models and integrated parser and history repository.
    - App orchestrates full flow: fetch → parse → history → OpenAI → export.
    - Architecture docs updated. Legacy game model stubbed out.

- Functional style/architecture: Mostly compliant.
    - Parsing and mapping are pure; side effects at app/history/export/OpenAI edges. Consider eliminating the temp file in parser if the library allows.
    - Improve de‑dupe in history to be ID‑based with a merge policy.

- Model invariants:
    - `TrainingPlan` matches `trainingPlanSchema()`; `TaskType` enum in sync. Good.
    - `MarkdownRenderer` should render `notes` unconditionally or make `notes` optional across model + schema. Current mismatch flagged.

- Tests:
    - Parser tests are solid and deterministic.
    - Missing tests for history merging and markdown rendering.

- Git hygiene/secrets: Action required before pushing.
    - Remove all files under `output/` from version control; ensure `output/` in `.gitignore`.
    - Remove `env.sh` from version control; rotate `LICHESS_TOKEN` and `OPENAI_API_KEY`. Add `.env`/`env.sh` to `.gitignore`.

Checklist before push:
- [ ] Remove `output/` contents from git and ignore the folder.
- [ ] Remove `env.sh` from git, rotate tokens/keys, and ignore secrets files.
- [ ] Fix `MarkdownRenderer` notes handling to match the `TrainingPlan` model/schema.
- [ ] Consider switching history de‑dupe to ID‑based with a merge policy.
- [ ] Add unit tests:
    - [ ] `GameHistoryRepositoryTest` for merges and de‑dupe.
    - [ ] `MarkdownRendererTest` for notes rendering and layout.
- [ ] Optionally document the temp‑file approach in `PgnParser` or remove it if the library supports in‑memory parsing.
- [ ] Run tests locally.

Finally, please run:
- `./gradlew test`
- If everything looks good: `git push`
