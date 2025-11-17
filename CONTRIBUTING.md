# Contributing to kotlin-chess-coach

This repo is primarily for my own use, but future me (and any collaborators) should follow the same rules so the project stays sane.

## 1. Read the docs first

Before making changes:

- **Architecture:** see `docs/architecture.md`
- **Engineering practices:** see `docs/engineering-guidelines.md`

Those describe:
- Overall data flow (Lichess → GameBatch → OpenAI → TrainingPlan → output)
- Functional style and testing expectations

## 2. Branching & workflow

- Default branch: `main`
- Preferred workflow:
    - Create a feature branch from `main`:
        - `git checkout -b feature/short-description`
    - Make changes + commits on the feature branch.
    - Merge back into `main` only after tests pass.

If you’re just hacking solo and don’t care about branches for a tiny change, you *may* commit directly to `main`, but still respect the rules below.

## 3. What **not** to commit

Never commit:

- Secrets or environment files:
    - `.env`
    - `env.sh`
    - Any file containing API keys or tokens
- Generated output:
    - `output/` directory
    - Any timestamped training-plan files
    - Any game history JSON backups
- Local IDE / OS noise:
    - `.idea/`
    - `*.iml`
    - `.DS_Store`
    - `build/`, `.gradle/`

If you accidentally add one of these, remove it with:

```bash
git rm --cached <file-or-dir>
