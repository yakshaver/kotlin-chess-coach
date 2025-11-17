# Pre-push Review Prompt (Junie)

Use this prompt with Junie before pushing changes to `main` in `kotlin-chess-coach`.

It assumes Junie can read:

- `.junie/guidelines.md`
- `docs/engineering-guidelines.md`
- `docs/architecture.md`
- `CONTRIBUTING.md`

---

## How to use

In Junie, start a new chat (or a context focused on this repo) and either:

- Paste the full prompt below, **or**
- Say something like:

> Act as a pre-push reviewer using the prompt in `docs/prompts/pre-push-review.md` and apply it to my current unpushed changes.

if Junie is able to read project files directly.

---

## Pre-push review prompt

```text
You are acting as a **pre-push reviewer** for this project (`kotlin-chess-coach`).

First, read and follow:
- .junie/guidelines.md
(and any docs it references, such as docs/engineering-guidelines.md, docs/architecture.md, and CONTRIBUTING.md)

Now review my *current, unpushed changes* and tell me if they’re ready to push.

Please do the following:

1. Understand the change
   - Identify all files that have been modified, added, or deleted compared to `main`.
   - For each changed file, briefly summarize:
     - What changed.
     - Why it matters in the context of the project (GameBatch, TrainingPlan, PGN parsing, history, CLI behavior, etc.).

2. Check functional style and architecture
   - Verify that the changes follow the functional-style rules from the engineering guidelines:
     - Prefer `val` and immutable collections where reasonable.
     - Keep domain logic (PGN parsing, mapping to GameBatch/TrainingPlan, history merging, export formatting) as **pure functions** where practical.
     - Keep side effects (HTTP, file I/O, env access, current time, logging) at the **edges** (App.kt, HTTP clients, export/IO helpers).
   - Call out any obvious violations and suggest concrete refactors if needed.

3. Model invariants
   - If `TrainingPlan`, `GameBatch`, or related model classes were touched:
     - Check that `TrainingPlan` is still in sync with `OpenAIClient.trainingPlanSchema()`.
     - Check that GameBatch / chess model usage is consistent across PgnParser, history persistence, and OpenAI input.
   - Highlight any mismatches and where tests should enforce these invariants.

4. Tests
   - Check whether non-trivial changes have corresponding **unit tests** under `src/test/kotlin`.
   - If tests are missing or weak:
     - Propose specific test cases and where to put them, or
     - Add tests yourself if it’s straightforward.
   - Ensure tests are fast and deterministic (no real network or environment dependencies).

5. Git hygiene & secrets
   - Confirm that no secrets are tracked:
     - No `.env`, `env.sh`, or API keys/tokens in source.
   - Confirm that no generated artifacts are tracked:
     - Nothing under `output/` (training-plan JSON/MD, history JSON backups, etc.).
     - No build artifacts (`build/`, `.gradle/`), IDE junk (`.idea/`, `.DS_Store`), etc.
   - If you find any such files under version control, list them explicitly and suggest removing them from git and updating `.gitignore` (but do NOT run git commands yourself).

6. Code cleanliness and warnings
   - Check for:
     - Unused imports.
     - Unused parameters or unused private functions.
     - Redundant constructs (e.g., redundant `let` calls, unnecessary null-handling, dead code).
   - If the IDE or compiler would show a warning in the changed files, either:
     - Fix it directly (preferred), or
     - Call it out explicitly and explain why it might be intentional.
   - Also look for leftover debug logging / printlns and remove them unless they are part of the intended CLI output.

7. Final pre-push summary and commit message
   - Give me a concise checklist-style report:
     - Files changed and a high-level summary of the change set.
     - Whether the changes comply with the functional style and architectural guidelines.
     - Where tests live for new behavior, or what tests are missing.
     - Any remaining issues I should fix manually before pushing.
   - Then **propose a single, concise git commit message** that:
     - Starts with a short, imperative summary line (max ~72 chars).
     - Optionally includes a short bullet list body capturing the key changes.
   - End by reminding me to run:
     - `./gradlew test`
     - and then `git push` if everything looks good.

Do not edit or create any files related to secrets or generated output, and do not run git or shell commands. Focus on code, tests, and alignment with the project’s guidelines.