package com.taplink.app.data.support

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Minimal DeepSeek chat-completions client.
 *
 * Deliberately small: no streaming, one request per user turn, a short answer budget, and no busy
 * retries (a retry would cost the user twice). A production build must not ship a key at all; it
 * should call our own backend, which keeps the key and enforces per-account quotas.
 */
class DeepSeekSupport(
    private val apiKey: String,
    private val model: String,
    private val quota: SupportQuota,
    private val endpoint: String = ENDPOINT,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** Requests left today; shown in the chat header so the limit is never a surprise. */
    fun remaining(): Int = quota.remaining()

    suspend fun answer(facts: SupportFacts, turns: List<SupportTurn>): AssistantResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext AssistantResult.Failure(FailureKind.NOT_CONFIGURED)
        val history = SupportPrompt.history(turns)
        if (history.isEmpty()) return@withContext AssistantResult.Failure(FailureKind.EMPTY)
        // Checked before the request, so a spent allowance never produces a billable call.
        if (!quota.tryConsume()) return@withContext AssistantResult.Failure(FailureKind.QUOTA_REACHED)

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SupportPrompt.system(facts)))
                history.forEach { turn ->
                    put(JSONObject().put("role", if (turn.fromUser) "user" else "assistant").put("content", turn.text))
                }
            })
            put("max_tokens", MAX_TOKENS)
            put("temperature", 0.3)
            // Without this the model spends most of the budget on hidden reasoning, which is slower
            // and more expensive for a short support answer.
            put("reasoning_effort", "low")
            put("stream", false)
        }

        val connection = try {
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
            }
        } catch (_: Exception) {
            return@withContext AssistantResult.Failure(FailureKind.OFFLINE)
        }

        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                status == 401 || status == 403 || status == 402 -> AssistantResult.Failure(FailureKind.UNAUTHORIZED)
                status == 429 -> AssistantResult.Failure(FailureKind.RATE_LIMITED)
                status !in 200..299 -> AssistantResult.Failure(FailureKind.UNAVAILABLE)
                else -> parse(body)
            }
        } catch (_: SocketTimeoutException) {
            AssistantResult.Failure(FailureKind.OFFLINE)
        } catch (_: IOException) {
            AssistantResult.Failure(FailureKind.OFFLINE)
        } catch (_: Exception) {
            AssistantResult.Failure(FailureKind.UNAVAILABLE)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): AssistantResult {
        val text = runCatching {
            JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
        }.getOrNull().orEmpty()
        // Models occasionally wrap short answers in emphasis markers; strip them for plain chat.
        val clean = text.replace("**", "").replace(Regex("(?m)^#{1,6}\\s*"), "").trim()
        return if (clean.isEmpty()) AssistantResult.Failure(FailureKind.EMPTY)
        else AssistantResult.Answer(clean.take(MAX_ANSWER_CHARS))
    }

    companion object {
        const val ENDPOINT = "https://api.deepseek.com/chat/completions"

        /** Includes hidden reasoning tokens, so it is larger than the visible answer limit. */
        const val MAX_TOKENS = 480
        const val MAX_ANSWER_CHARS = 900
    }
}
