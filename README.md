# Kotlin Chess Coach

A small Kotlin tool that:

1. Fetches your recent games from Lichess  
2. Infers your current level from game results  
3. Calls the OpenAI API to generate a structured training plan  
4. Exports the plan as both JSON and Markdown into an `output/` directory, with timestamped filenames.

---

## Prerequisites

- Java 21 (JDK)
- Kotlin (via Gradle wrapper, no separate install needed)
- An OpenAI API key with access to a model like `gpt-4.1-mini` or `gpt-5.1`
- A Lichess account + API token

---

## Environment Variables

Set the following environment variables (locally or via `.env` + EnvFile in IntelliJ):

- `LICHESS_USER` — your Lichess username  
- `LICHESS_TOKEN` — a Lichess API token with read access  
- `OPENAI_API_KEY` — your OpenAI API key  
- (optional) `OPENAI_MODEL` — model name, e.g. `gpt-4.1-mini` or `gpt-5.1`  
  - If omitted, the default in `OpenAIClient` is used.

Example `.env`:

```env
LICHESS_USER=ppointmass
LICHESS_TOKEN=your_lichess_token_here
OPENAI_API_KEY=your_openai_key_here
OPENAI_MODEL=gpt-5.1
