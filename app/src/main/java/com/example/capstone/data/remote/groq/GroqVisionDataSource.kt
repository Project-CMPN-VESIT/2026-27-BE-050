package com.example.capstone.data.remote.groq

import android.util.Base64
import android.util.Log
import com.example.capstone.BuildConfig
import com.example.capstone.data.MedReadyItem
import com.example.capstone.data.MedReadyScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

class GroqVisionDataSource {

    companion object {

        private const val TAG = "GroqVision"

        private const val ENDPOINT =
            "https://api.groq.com/openai/v1/chat/completions"

        /*
         * Current Groq vision model.
         */
        private const val MODEL =
            "qwen/qwen3.8-27b"

        /*
         * Keep this reasonably small because your current
         * account has an 8000 TPM limit.
         */
        private const val MAX_COMPLETION_TOKENS = 600

        /*
         * Retry only when Groq returns HTTP 429.
         */
        private const val MAX_RETRIES = 2

        private const val RETRY_DELAY_MS = 10_000L
    }

    fun isConfigured(): Boolean {
        return BuildConfig.GROQ_API_KEY.isNotBlank()
    }

    suspend fun analyzeMedicineKit(
        imageBytes: ByteArray
    ): MedReadyScanResult = withContext(Dispatchers.IO) {

        if (!isConfigured()) {
            throw IOException(
                "Groq API key is not configured"
            )
        }

        if (imageBytes.isEmpty()) {
            throw IOException(
                "Image is empty"
            )
        }

        Log.d(TAG, "========================================")
        Log.d(TAG, "Starting MedReady Vision Analysis")
        Log.d(TAG, "Model: $MODEL")
        Log.d(TAG, "Image size: ${imageBytes.size} bytes")
        Log.d(TAG, "========================================")

        /*
         * ============================================================
         * IMAGE
         * ============================================================
         */

        val base64Image =
            Base64.encodeToString(
                imageBytes,
                Base64.NO_WRAP
            )

        val imageUrl =
            "data:image/jpeg;base64,$base64Image"

        /*
         * ============================================================
         * PROMPT
         * ============================================================
         *
         * Keep this short.
         */

        val systemPrompt = """
            You are MedReady AI.

            Analyze the first-aid or medicine kit in the image.

            Identify ONLY items clearly visible.

            For every visible item return:
            - name
            - status: "Detected" or "Expired"
            - expiryDate: "" if not visible
            - isEssential: true or false

            Rules:
            - Never invent items.
            - Never invent expiry dates.
            - Only use "Expired" when a visible expiry date has passed.
            - If expiry is unreadable, use "".
            - Do not say an item is missing just because it is not visible.

            readinessScore: integer from 0 to 100.
            warnings: number of visible expired/problematic items.
            summary: short description.

            Return JSON only.
        """.trimIndent()

        /*
         * ============================================================
         * MESSAGES
         * ============================================================
         */

        val messages =
            JSONArray().apply {

                /*
                 * System
                 */
                put(
                    JSONObject().apply {

                        put(
                            "role",
                            "system"
                        )

                        put(
                            "content",
                            systemPrompt
                        )
                    }
                )

                /*
                 * User + image
                 */
                put(
                    JSONObject().apply {

                        put(
                            "role",
                            "user"
                        )

                        put(
                            "content",
                            JSONArray().apply {

                                put(
                                    JSONObject().apply {

                                        put(
                                            "type",
                                            "text"
                                        )

                                        put(
                                            "text",
                                            """
                                            Analyze this medicine kit image.
                                            Return only the required JSON object.
                                            """.trimIndent()
                                        )
                                    }
                                )

                                put(
                                    JSONObject().apply {

                                        put(
                                            "type",
                                            "image_url"
                                        )

                                        put(
                                            "image_url",
                                            JSONObject().apply {

                                                put(
                                                    "url",
                                                    imageUrl
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }

        /*
         * ============================================================
         * REQUEST
         * ============================================================
         */

        val request =
            JSONObject().apply {

                put(
                    "model",
                    MODEL
                )

                put(
                    "messages",
                    messages
                )

                /*
                 * IMPORTANT:
                 *
                 * Disable Qwen thinking.
                 *
                 * This is the main fix for your latest error.
                 */
                put(
                    "reasoning_effort",
                    "none"
                )

                /*
                 * Do not return <think> content.
                 */
                put(
                    "reasoning_format",
                    "hidden"
                )

                /*
                 * Now JSON mode is appropriate because
                 * reasoning is disabled.
                 */
                put(
                    "response_format",
                    JSONObject().apply {
                        put(
                            "type",
                            "json_object"
                        )
                    }
                )

                /*
                 * Non-thinking mode is better with a
                 * moderate temperature.
                 */
                put(
                    "temperature",
                    0.7
                )

                /*
                 * Small output because the result itself
                 * is very small.
                 */
                put(
                    "max_completion_tokens",
                    MAX_COMPLETION_TOKENS
                )

                /*
                 * We don't need streaming.
                 */
                put(
                    "stream",
                    false
                )
            }

        Log.d(
            TAG,
            "Request prepared"
        )

        /*
         * ============================================================
         * SEND + RETRY
         * ============================================================
         */

        var attempt = 0

        while (attempt <= MAX_RETRIES) {

            try {

                Log.d(
                    TAG,
                    "Sending request. Attempt ${attempt + 1}/${MAX_RETRIES + 1}"
                )

                val responseBody =
                    sendRequest(
                        request
                    )

                return@withContext parseResponse(
                    responseBody
                )

            } catch (e: RateLimitException) {

                if (attempt < MAX_RETRIES) {

                    Log.w(
                        TAG,
                        "Groq rate limit reached."
                    )

                    Log.w(
                        TAG,
                        "Waiting ${RETRY_DELAY_MS / 1000} seconds..."
                    )

                    delay(
                        RETRY_DELAY_MS
                    )

                    attempt++

                } else {

                    throw IOException(
                        "Groq rate limit reached. Please wait and try again.",
                        e
                    )
                }

            } catch (e: ModelNotFoundException) {
                Log.w(TAG, "Groq model not found; trying alternative models")
                val alternatives = listOf("gpt-image-1", "gpt-4o-mini-vision", "groq/vision-stable")
                var altAttempted = false
                for (alt in alternatives) {
                    if (alt == MODEL) continue
                    try {
                        Log.d(TAG, "Trying alternative model: $alt")
                        request.put("model", alt)
                        val altResponse = sendRequest(request)
                        return@withContext parseResponse(altResponse)
                    } catch (ex: RateLimitException) {
                        throw ex
                    } catch (ex: Exception) {
                        Log.w(TAG, "Alternative model $alt failed: ${ex.message}")
                        altAttempted = true
                    }
                }
                if (altAttempted) {
                    throw IOException("All configured vision models failed. Please verify your Groq API key and available models.")
                } else {
                    throw IOException("Groq model not found and no alternatives configured.")
                }
            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Groq Vision request failed",
                    e
                )

                throw if (e is IOException) {
                    e
                } else {
                    IOException(
                        "Groq Vision request failed: ${e.message}",
                        e
                    )
                }
            }
        }

        throw IOException(
            "Groq Vision request failed."
        )

    }

    /*
     * ================================================================
     * HTTP REQUEST
     * ================================================================
     */

    private fun sendRequest(
        request: JSONObject
    ): String {

        val connection =
            URL(ENDPOINT)
                .openConnection() as HttpURLConnection

        try {

            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                30_000

            connection.readTimeout =
                60_000

            connection.doInput =
                true

            connection.doOutput =
                true

            connection.useCaches =
                false

            connection.setRequestProperty(
                "Authorization",
                "Bearer ${BuildConfig.GROQ_API_KEY}"
            )

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            val requestBody =
                request.toString()

            connection.outputStream.use {
                    outputStream ->

                outputStream.write(
                    requestBody.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

                outputStream.flush()
            }

            Log.d(
                TAG,
                "Request sent to Groq"
            )

            val responseCode =
                connection.responseCode

            Log.d(
                TAG,
                "Groq HTTP Response Code: $responseCode"
            )

            /*
             * ========================================================
             * SUCCESS
             * ========================================================
             */

            if (responseCode in 200..299) {

                val body =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                Log.d(
                    TAG,
                    "Groq response received successfully"
                )

                return body
            }

            /*
             * ========================================================
             * ERROR
             * ========================================================
             */

            val errorBody =
                connection.errorStream
                    ?.bufferedReader()
                    ?.use {
                        it.readText()
                    }
                    .orEmpty()

            Log.e(
                TAG,
                "Groq API ERROR: $errorBody"
            )

            /*
             * RATE LIMIT
             */

            if (responseCode == 429) {

                throw RateLimitException(
                    errorBody
                )
            }

            // MODEL NOT FOUND -> fallback
            if (responseCode == 404) {
                throw ModelNotFoundException(errorBody)
            }

            throw IOException(
                "Groq API returned HTTP $responseCode: $errorBody"
            )

        } finally {

            connection.disconnect()
        }
    }

    /*
     * ================================================================
     * PARSE GROQ RESPONSE
     * ================================================================
     */

    private fun parseResponse(
        responseBody: String
    ): MedReadyScanResult {

        Log.d(
            TAG,
            "Parsing Groq API response..."
        )

        val response =
            JSONObject(responseBody)

        /*
         * Check API error.
         */

        if (response.has("error")) {

            val message =
                response
                    .optJSONObject("error")
                    ?.optString(
                        "message"
                    )
                    ?: "Unknown Groq API error"

            throw IOException(
                message
            )
        }

        /*
         * Choices
         */

        val choices =
            response.optJSONArray(
                "choices"
            )

        if (
            choices == null ||
            choices.length() == 0
        ) {

            throw IOException(
                "Groq returned no choices."
            )
        }

        /*
         * Message
         */

        val message =
            choices
                .getJSONObject(0)
                .optJSONObject("message")

                ?: throw IOException(
                    "Groq response does not contain message."
                )

        /*
         * With reasoning disabled + hidden,
         * content should now contain ONLY JSON.
         */

        var content =
            message.optString(
                "content",
                ""
            ).trim()

        Log.d(
            TAG,
            "AI JSON response:"
        )

        Log.d(
            TAG,
            content
        )

        if (content.isBlank()) {

            throw IOException(
                "Groq returned empty content."
            )
        }

        /*
         * Safety cleanup.
         */

        content =
            cleanJsonResponse(
                content
            )

        /*
         * Parse JSON.
         */

        val json =
            parseAiJson(
                content
            )

        /*
         * ============================================================
         * ITEMS
         * ============================================================
         */

        val itemsArray =
            json.optJSONArray(
                "items"
            ) ?: JSONArray()

        val items =
            mutableListOf<MedReadyItem>()

        for (
        i in 0 until itemsArray.length()
        ) {

            val item =
                itemsArray.optJSONObject(i)
                    ?: continue

            val name =
                item.optString(
                    "name",
                    "Unknown Item"
                ).trim()

            if (name.isBlank()) {
                continue
            }

            val status =
                item.optString(
                    "status",
                    "Detected"
                ).trim()

            val expiryDate =
                item.optString(
                    "expiryDate",
                    ""
                ).trim()

            val isEssential =
                item.optBoolean(
                    "isEssential",
                    true
                )

            items.add(
                MedReadyItem(
                    name = name,
                    status = status,
                    expiryDate = expiryDate,
                    isEssential = isEssential
                )
            )
        }

        /*
         * ============================================================
         * RESULT VALUES
         * ============================================================
         */

        val readinessScore =
            json.optInt(
                "readinessScore",
                0
            ).coerceIn(
                0,
                100
            )

        val warnings =
            json.optInt(
                "warnings",
                0
            ).coerceAtLeast(0)

        val summary =
            json.optString(
                "summary",
                "No summary available."
            ).trim()

        /*
         * ============================================================
         * FINAL RESULT
         * ============================================================
         */

        val result =
            MedReadyScanResult(

                id =
                    UUID.randomUUID()
                        .toString(),

                timestamp =
                    System.currentTimeMillis(),

                itemCount =
                    items.count {
                        it.status.equals(
                            "Detected",
                            ignoreCase = true
                        )
                    },

                readinessScore =
                    readinessScore,

                warnings =
                    warnings,

                summary =
                    summary.ifBlank {
                        "No summary available."
                    },

                items =
                    items
            )

        Log.d(
            TAG,
            "========================================"
        )

        Log.d(
            TAG,
            "MEDREADY ANALYSIS SUCCESS"
        )

        Log.d(
            TAG,
            "Model: $MODEL"
        )

        Log.d(
            TAG,
            "Items: ${result.itemCount}"
        )

        Log.d(
            TAG,
            "Readiness: ${result.readinessScore}"
        )

        Log.d(
            TAG,
            "Warnings: ${result.warnings}"
        )

        Log.d(
            TAG,
            "Summary: ${result.summary}"
        )

        Log.d(
            TAG,
            "========================================"
        )

        return result
    }

    /*
     * ================================================================
     * CLEAN JSON
     * ================================================================
     */

    private fun cleanJsonResponse(
        original: String
    ): String {

        var content =
            original.trim()

        /*
         * Remove markdown fences if they somehow appear.
         */

        if (
            content.contains(
                "```json",
                ignoreCase = true
            )
        ) {

            content =
                content
                    .substringAfter(
                        "```json"
                    )
                    .substringBeforeLast(
                        "```"
                    )
                    .trim()
        }

        else if (
            content.contains("```")
        ) {

            content =
                content
                    .substringAfter(
                        "```"
                    )
                    .substringBeforeLast(
                        "```"
                    )
                    .trim()
        }

        return content
    }

    /*
     * ================================================================
     * PARSE JSON
     * ================================================================
     */

    private fun parseAiJson(
        content: String
    ): JSONObject {

        /*
         * JSON mode should make this work directly.
         */

        try {

            return JSONObject(
                content
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Direct JSON parsing failed. Trying extraction."
            )
        }

        /*
         * Fallback.
         */

        val firstBrace =
            content.indexOf("{")

        val lastBrace =
            content.lastIndexOf("}")

        if (
            firstBrace >= 0 &&
            lastBrace > firstBrace
        ) {

            val extracted =
                content.substring(
                    firstBrace,
                    lastBrace + 1
                )

            try {

                return JSONObject(
                    extracted
                )

            } catch (e: Exception) {

                throw IOException(
                    "AI returned invalid JSON: $content",
                    e
                )
            }
        }

        throw IOException(
            "AI did not return valid JSON. Response: $content"
        )
    }

    /*
     * ================================================================
     * RATE LIMIT EXCEPTION
     * ================================================================
     */

    private class RateLimitException(
        message: String
    ) : IOException(message)

    private class ModelNotFoundException(
        message: String
    ) : IOException(message)

}