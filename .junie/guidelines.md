# Junie Guidelines for `kotlin-chess-coach`

These guidelines tell Junie how to behave in this project.

They **supplement** (do not replace):

- `docs/architecture.md`
- `docs/engineering-guidelines.md`
- `CONTRIBUTING.md`

If anything here conflicts with those docs, prefer the docs in `docs/` and `CONTRIBUTING.md`.

---

## 1. Project context

This is a Kotlin CLI tool that:

- Fetches recent games from **Lichess**.
- Parses them into a structured **GameBatch**.
- Calls **OpenAI** to generate a **TrainingPlan**.
- Exports JSON + Markdown plans and maintains long-term game history in `output/`.

---

## 2. Coding style & design

### 2.1 Functional style (high-level)

For **full details**, follow `docs/engineering-guidelines.md`. Junie should assume:

- Immutability by default (`val`, immutable collections).
- Domain logic (parsing, mapping, transformations) implemented as **pure functions**.
- Side effects (HTTP, file I/O, env access, logging, current time) pushed to the **edges**:
  - `App.kt`
  - HTTP client wrappers
  - Export/I/O helpers
- Use Kotlin’s collection and higher-order functions (`map`, `filter`, etc.) instead of imperative loops where it improves clarity.
- Prefer non-nullable types and safe null handling; avoid `!!` unless justified.

When generating or refactoring code, Junie should **enforce** these rules, not re-invent its own style.

### 2.2 Data model invariants

- `coach.model.TrainingPlan` must stay in sync with `OpenAIClient.trainingPlanSchema()`.
- `coach.model.GameBatch` and its game type (`ChessGame` or `GameSummary`) are:
  - The canonical representation of parsed games.
  - The structure passed to OpenAI and used by history features.
- If Junie changes these models, it must also update:
  - The schema,
  - Exporters,
  - And tests that depend on them.

---

## 3. Testing

Junie should assume that **every non-trivial change requires tests**:

- Prefer tests for pure functions (PGN → `GameBatch`, history merging, training plan export, etc.).
- Keep tests fast and deterministic; no external network calls.
- Place tests under `src/test/kotlin/...` and use the existing test framework (JUnit / Kotest).

Before suggesting a push, Junie should remind you to run:

- `./gradlew test`

---

## 4. PGN / Lichess parsing

- `PgnParser` and related chess modules should:
  - Expose a clear, pure API that returns `GameBatch`.
  - Prefer **kchesslib** for PGN parsing instead of brittle regex.
- If any regex or heuristics remain, Junie should:
  - Keep them small,
  - Document them clearly,
  - Ensure they are covered by tests.

---

## 5. OpenAI & external services

Junie must:

- Never hard-code API keys, tokens, or secrets.
- Assume secrets come from env vars or other non-tracked config.
- Respect the JSON schema contract for `TrainingPlan`.
- Handle error cases (invalid response, timeout, etc.) gracefully in suggestions.

Any changes to the OpenAI request/response shape must keep:

- `TrainingPlan`
- `trainingPlanSchema()`
- Tests

in sync.

---

## 6. Files, output, and git hygiene

Junie should follow the “What **not** to commit” rules in `CONTRIBUTING.md` and enforce them in suggestions:

- Never suggest committing:
  - `output/` or its contents.
  - `.env`, `env.sh`, or other secret-bearing files.
  - Build artifacts (`build/`, `.gradle/`), `.idea/`, `.DS_Store`, etc.
- If a new generated path is added, keep it under `output/` or add it to `.gitignore`.

---

## 7. Using Junie as a pre-push reviewer

Before pushing, you can ask Junie to act as a lightweight **pre-push review**:

Junie should:

1. Look at files changed since `main`.
2. Summarize what changed in each file.
3. Check that changes:
  - Follow the functional style and invariants above.
  - Keep side effects at the edges.
4. Verify that:
  - New behavior has appropriate tests.
  - No secrets or generated output are being tracked.
5. Suggest obvious cleanups (unused imports, debug logging, etc.).
6. End with a short checklist and a reminder to run `./gradlew test`.

For the detailed pre-push review prompt, see:
- `docs/prompts/pre-push-review.md`