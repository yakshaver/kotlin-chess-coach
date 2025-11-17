# Technical Debt Backlog

This file tracks deliberate technical debt and near-term refactors for `kotlin-chess-coach`.

Each item should be:
- Small enough for a 1–2 hour session, or clearly marked as larger.
- Justified (why it matters).
- Testable (what should be true when it’s done).

Use this file together with:
- `.junie/guidelines.md`
- `docs/architecture.md`
- `docs/engineering-guidelines.md`
- `docs/prompts/pre-push-review.md`

---

## Next Coding Session

These are the highest-priority items to tackle in the next focused session.

1. [ ] Add tests for GameHistoryRepository merge & de-duplication
    - **Context**: `GameHistoryRepository.mergeBatches` currently merges with `(existing.games + new.games).distinct()`, relying on full data-class equality. There are no dedicated tests for merge semantics.
    - **Why it matters**:
        - The same Lichess game may be fetched multiple times with improved metadata (ratings, timestamps, openings).
        - Full-object `distinct()` may either keep duplicates or drop the more useful record.
    - **Suggested approach**:
        - Add `GameHistoryRepositoryTest` in `src/test/kotlin/coach/history/`.
        - Start by testing current behavior: duplicate `id`s, different metadata, mixed existing/new games.
        - Once tests exist, they can drive any future refinement of the merge policy (see Backlog item “Refine id-based de-dupe policy”).
    - **Tests / verification**:
        - New tests describe expected behavior for duplicate games and simple merges.
        - Tests are fast, deterministic, and do not hit the real filesystem if possible (e.g., use a temp directory or injected path).

2. [ ] Add PgnParser edge-case tests for multi-game, spacing, and odd TimeControl values
    - **Context**: `PgnParser` has good coverage for “normal” PGNs, but the pre-push review noted possible brittleness around splitting and edge cases (multiple blank lines, comments, weird `TimeControl` strings).
    - **Why it matters**:
        - Lichess exports sometimes contain comments, different line endings, or unusual time control formats.
        - Robust parsing is core to the value of `GameBatch` and downstream training plans.
    - **Suggested approach**:
        - Extend `PgnParserTest` with cases for:
            - Multiple games separated by extra blank lines and/or comments before `[Event]`.
            - CRLF vs LF (`\r\n` vs `\n`) line endings.
            - `TimeControl` strings like `"60+0"`, `"600+5"`, and malformed strings, verifying normalization and fallbacks.
            - Result `"*"` mapping to an “unknown”/fallback `GameResult`.
    - **Tests / verification**:
        - Additional test methods in `PgnParserTest` clearly document these edge cases.
        - All tests remain deterministic and offline.

3. [ ] Add an OpenAI schema drift / parity test for TrainingPlan
    - **Context**: `TrainingPlan` is currently in sync with `OpenAIClient.trainingPlanSchema()`, but there is no automated check to prevent future drift.
    - **Why it matters**:
        - Any mismatch between the model and the schema can cause runtime errors from `response_format` or JSON decoding failures.
    - **Suggested approach**:
        - Add `OpenAISchemaTest` in `src/test/kotlin/coach/openai/`.
        - Construct a minimal valid `TrainingPlan` instance and serialize it.
        - Assert that the keys/types match what `trainingPlanSchema()` describes (at least for top-level keys and task enum values).
    - **Tests / verification**:
        - A failing test clearly points to schema/model mismatch.
        - Adjustments to `TrainingPlan` or schema must update both to keep tests green.

---

## Backlog

These are important but not necessarily “next session” items. They can be pulled into the **Next Coding Session** section as priorities shift.

### History / Persistence

- [ ] Refine GameHistoryRepository de-dupe policy to be id-based
    - **Context**: Even after tests exist, the merge strategy should probably group by a stable key (`id`) and pick a “best” record when duplicates exist.
    - **Why it matters**:
        - Prevents subtle duplicate entries and ensures the richest metadata is kept.
    - **Possible direction**:
        - Group by `id` and choose the entry with:
            - Non-null `startedAt` over null, and/or
            - Higher `yourRating` or more complete metadata.
        - Make the merge policy explicit and documented in `GameHistoryRepository` and tests.

### PGN / Parsing / GameBatch

- [ ] Optionally replace naive text splitting with a library-based PGN splitter
    - **Context**: Current approach splits PGN text on `[Event` boundaries and is covered by tests, but kchesslib may offer a more robust multi-game parsing API.
    - **Why it matters**:
        - Reduces reliance on string heuristics; delegates structure to the library.
    - **Direction**:
        - Investigate whether kchesslib can iterate over multiple games directly.
        - If available and stable, refactor `PgnParser` to favor that path.

- [ ] Revisit defaulting to PlayerColor.WHITE when username doesn’t match either side
    - **Context**: When the provided `player` username doesn’t match White or Black in headers, color is defaulted to `WHITE`.
    - **Why it matters**:
        - This may silently mask PGN/user mismatches and distort result perspective.
    - **Possible direction**:
        - Introduce an `UNKNOWN` color or assert/warn in such situations.
        - Add tests describing the chosen behavior.

- [ ] Remove or document temp-file usage in PgnParser
    - **Context**: `parseWithKChessLib` writes a temp file to satisfy kchesslib. This is a side effect in what conceptually should be pure domain logic.
    - **Why it matters**:
        - Makes parsing dependent on filesystem behavior and slightly harder to test.
    - **Possible direction**:
        - If kchesslib offers parsing from an in-memory string/stream, prefer that.
        - If not, document the temp-file adapter explicitly and ensure it’s covered by tests and clear comments.

### OpenAI / TrainingPlan / Error Handling

- [ ] Improve OpenAIClient diagnostics and timeouts
    - **Context**: `OpenAIClient` currently errors when `content` is missing, but diagnostics (HTTP status, truncated body, etc.) and timeout handling are minimal.
    - **Why it matters**:
        - Better error messages make debugging easier when the API misbehaves or quota is exceeded.
    - **Possible direction**:
        - Enhance error messages to include status code and a truncated response body.
        - Configure `HttpClientFactory` with reasonable request timeouts and optional retry strategy.

### Tooling / Process / Docs

- [ ] Keep docs/prompts/pre-push-review.md & docs/tech-debt.md in sync with actual practice
    - **Context**: Pre-push review and tech-debt docs codify process and expectations. They will evolve as the project grows.
    - **Why it matters**:
        - Avoids “documentation drift” where process docs no longer reflect reality.
    - **Possible direction**:
        - When process changes (e.g., new steps, new tools), update both files together.
        - Use Junie to propose changes based on how the project is actually being worked.

---

## Maintenance Rules

When you finish a coding session:

1. Move any completed items from **Next Coding Session** to a checked-off state (`[x]`) and optionally annotate them with a commit hash or short note.
2. Add new debt items you discovered, placing them either under:
    - **Next Coding Session** (if urgent), or
    - The appropriate section under **Backlog**.
3. Keep this file small and focused; if an item becomes large, split it into smaller sub-tasks or create a dedicated GitHub issue and link it here.