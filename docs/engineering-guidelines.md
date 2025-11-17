# Engineering Guidelines

## 1. Kotlin + Functional Style

This project is written in Kotlin with a **functional-leaning style**. Object-oriented modeling is still useful, but we favor:

- Immutability over mutation
- Pure functions over stateful methods
- Functions as values (higher-order functions)
- Side effects pushed to the edges

### 1.1 Immutability

- Prefer `val` over `var`. Reach for `var` only when you genuinely need mutation and keep it local.
- Prefer immutable collections (`listOf`, `setOf`, `mapOf`) over mutable variants. If you must mutate, do it in a narrow scope.

### 1.2 Pure functions & side effects

- Domain logic (PGN parsing, `GameBatch` construction, training-plan transformations, history merging) should be **pure**:
  - No I/O, logging, or environment access.
  - No global state.
  - Same inputs → same outputs.
- Push side effects to the **edges**:
  - `App.kt`
  - HTTP clients (Lichess, OpenAI)
  - Export / I/O helpers
- This makes behavior easier to test, reason about, and refactor.

### 1.3 Functions as first-class values

- Treat functions as values:
  - Pass lambdas or function references into higher-order functions.
  - Return functions when it simplifies composition.
- Prefer using Kotlin’s standard higher-order functions:
  - `map`, `filter`, `flatMap`, `fold`, `onEach`, etc. for collection transforms.
- Scope functions (`let`, `run`, `apply`, `also`, `with`):
  - Use them to avoid null-heavy boilerplate or repetitive setup.
  - Avoid nesting them so deeply that control flow becomes hard to follow.

### 1.4 Null-safety

- Prefer **non-nullable** types as the default.
- Use:
  - Safe calls `?.`
  - Elvis `?:` with sensible defaults
- Avoid `!!` unless you can prove (and briefly comment) why it is safe.

### 1.5 Structure & size of functions

- Keep functions **small and focused**: one clear responsibility.
- If a function needs many parameters, consider:
  - Grouping arguments into a data class, or
  - Splitting the function into smaller pieces.
- Use **top-level functions** for stateless helpers instead of forcing everything into classes.

### 1.6 Coroutines & async

- Use `suspend` functions for asynchronous operations (HTTP, I/O).
- Respect **structured concurrency**:
  - Avoid `GlobalScope`.
  - Prefer explicit `CoroutineScope` or library-provided scopes.
- Inject dispatchers only where it improves control over threading; most domain logic should just be `suspend` and pure.

### 1.7 Arrow

- Arrow is available to model:
  - `Option` for explicit absence instead of “mysterious nullable”.
  - `Either` for recoverable errors.
- Use Arrow where it **clarifies** intent; don’t introduce FP abstractions just to be clever.
- If simple Kotlin stdlib is clearer, prefer that.

### 1.8 FP vs OOP (philosophy)

- We are not trying to write “pure FP Kotlin” or “pure OO”:
  - Use classes, data classes, and interfaces where they model the domain well.
  - Use FP ideas—immutability, pure functions, higher-order functions—to reduce state, make behavior predictable, and simplify testing.
- A good smell test:
  - Too many `var`s or mutable collections → probably needs refactor.
  - Complicated object graphs just to reuse code → consider pulling out pure functions.

---

## 2. Testing

- All new features or non-trivial changes must include **unit tests**.
- Focus on testing **pure functions** directly:
  - PGN → `GameBatch`
  - Game history merging
  - Training-plan post-processing
  - Export formatting
- Tests must be:
  - Fast
  - Deterministic
  - Not dependent on external services (Lichess, OpenAI, network).

Use the existing test stack (JUnit / Kotest) consistently.

Place tests under:

- `src/test/kotlin/...`

---

## 3. AI & Tooling (Junie / ChatGPT)

- When asking AI tools to modify code, always remind them:
  - “Respect functional style and immutability.”
  - “Keep domain logic pure; keep side effects at the edges.”
  - “Add or update unit tests for new behavior.”
- AI tools must **never**:
  - Add secrets to source code.
  - Touch `.env`, `env.sh`, or any secrets file.
  - Suggest committing anything under `output/`.

---

## 4. Code hygiene & invariants

- Keep:
  - `.env`, `env.sh`, and any secret-bearing files **out of git**.
  - Generated output (`output/`, timestamped plans, history JSON) **untracked**.
- `coach.model.TrainingPlan` must stay in sync with:
  - The JSON schema in `OpenAIClient.trainingPlanSchema()`.
- `coach.model.GameBatch` and its game type (`ChessGame` / `GameSummary`) are:
  - The canonical representation for parsed games and for calls into OpenAI.
- Don’t silently change fields without updating:
  - Schema,
  - Exporters,
  - Tests,
  - And any docs that depend on these models.