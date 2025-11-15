package coach.openai

import coach.model.GameBatch
import coach.model.TrainingPlan
import coach.http.HttpClientFactory
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class OpenAIClient(
    private val apiKey: String,
    private val model: String = "gpt-5.1-chat"
) {
    private val client = HttpClientFactory.default
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    suspend fun generateTrainingPlan(
        batch: GameBatch,
        currentRating: Int,
        targetRating: Int = 1500,
        horizonWeeks: Int = 2
    ): TrainingPlan {
        val batchJson = json.encodeToString(batch)

        val systemPrompt = """
            You are a chess coach helping an adult improver.

            The user plays mostly online rapid games and has about 30 minutes per day for structured training.
            You receive:
            1. A GameBatch JSON describing their recent games.
            2. Their current rating and target rating.

            Your job:
            - Identify their top recurring weaknesses from the GameBatch.
            - Design a concrete training plan for the next horizonWeeks within a 30-min/day budget.
            - Prioritize reduction of blunders, tactics, basic openings (simple principled repertoire),
              and fundamental endgames/strategy.

            You MUST respond ONLY with a TrainingPlan JSON object matching the provided JSON Schema.
            Do not include any extra text or commentary outside the JSON.
        """.trimIndent()

        val userPrompt = """
            Current rating: $currentRating
            Target rating: $targetRating
            Time budget: 30 minutes per day.
            Horizon weeks: $horizonWeeks

            GameBatch JSON:
            $batchJson
        """.trimIndent()

        val schema = trainingPlanSchema()

        val body = buildJsonObject {
            put("model", JsonPrimitive(model))
            putJsonArray("messages") {
                addJsonObject {
                    put("role", JsonPrimitive("system"))
                    put("content", JsonPrimitive(systemPrompt))
                }
                addJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive(userPrompt))
                }
            }
            put("temperature", JsonPrimitive(0.2))
            putJsonObject("response_format") {
                put("type", JsonPrimitive("json_schema"))
                putJsonObject("json_schema") {
                    put("name", JsonPrimitive("training_plan"))
                    put("schema", schema)
                    put("strict", JsonPrimitive(true))
                }
            }
        }

        val response = client.post("https://api.openai.com/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(json.encodeToString(body))
        }

        val text = response.body<String>()
        val root = json.parseToJsonElement(text).jsonObject

        val content = root["choices"]
            ?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("message")
            ?.jsonObject?.get("content")
            ?.jsonPrimitive?.content
            ?: error("No content in OpenAI response: $text")

        return json.decodeFromString<TrainingPlan>(content)
    }

    /**
     * JSON Schema for TrainingPlan, encoded as a JsonObject for response_format.
     */
    private fun trainingPlanSchema(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        putJsonObject("properties") {
            putJsonObject("planVersion") {
                put("type", JsonPrimitive("string"))
            }
            putJsonObject("targetRating") {
                put("type", JsonPrimitive("integer"))
            }
            putJsonObject("horizonWeeks") {
                put("type", JsonPrimitive("integer"))
            }
            putJsonObject("summary") {
                put("type", JsonPrimitive("string"))
            }
            putJsonObject("focusAreas") {
                put("type", JsonPrimitive("array"))
                putJsonObject("items") {
                    put("type", JsonPrimitive("string"))
                }
            }
            putJsonObject("dailySchedule") {
                put("type", JsonPrimitive("array"))
                putJsonObject("items") {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("dayIndex") {
                            put("type", JsonPrimitive("integer"))
                        }
                        putJsonObject("label") {
                            put("type", JsonPrimitive("string"))
                        }
                        putJsonObject("tasks") {
                            put("type", JsonPrimitive("array"))
                            putJsonObject("items") {
                                put("type", JsonPrimitive("object"))
                                putJsonObject("properties") {
                                    putJsonObject("type") {
                                        put("type", JsonPrimitive("string"))
                                        putJsonArray("enum") {
                                            add(JsonPrimitive("GAME"))
                                            add(JsonPrimitive("TACTICS"))
                                            add(JsonPrimitive("STUDY"))
                                            add(JsonPrimitive("REVIEW"))
                                            add(JsonPrimitive("ENDGAME"))
                                            add(JsonPrimitive("OPENING"))
                                            add(JsonPrimitive("OTHER"))
                                        }
                                    }
                                    putJsonObject("description") {
                                        put("type", JsonPrimitive("string"))
                                    }
                                    putJsonObject("minutes") {
                                        put("type", JsonPrimitive("integer"))
                                    }
                                }
                                putJsonArray("required") {
                                    add(JsonPrimitive("type"))
                                    add(JsonPrimitive("description"))
                                    add(JsonPrimitive("minutes"))
                                }
                                put("additionalProperties", JsonPrimitive(false))
                            }
                        }
                    }
                    putJsonArray("required") {
                        add(JsonPrimitive("dayIndex"))
                        add(JsonPrimitive("label"))
                        add(JsonPrimitive("tasks"))
                    }
                    put("additionalProperties", JsonPrimitive(false))
                }
            }
            putJsonObject("notes") {
                putJsonArray("type") {
                    add(JsonPrimitive("string"))
                    add(JsonPrimitive("null"))
                }
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("planVersion"))
            add(JsonPrimitive("targetRating"))
            add(JsonPrimitive("horizonWeeks"))
            add(JsonPrimitive("summary"))
            add(JsonPrimitive("focusAreas"))
            add(JsonPrimitive("dailySchedule"))
        }
        put("additionalProperties", JsonPrimitive(false))
    }
}
