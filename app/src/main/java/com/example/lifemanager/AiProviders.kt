package com.example.lifemanager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiHub {
    val PROVIDERS = listOf("gemini", "claude", "grok")

    val FOOD_PROMPT = """
کاربر لیست غذاهایی که خورده را می‌نویسد. کالری و وزن تقریبی هر مورد را به گرم تخمین بزن.
فقط و فقط یک آرایه JSON معتبر برگردان، بدون هیچ توضیح اضافه یا مارک‌داون، با این قالب:
[{"name":"نام غذا","grams":100,"kcal":250}]
""".trimIndent()

    private const val UA = "LifeManager/1.0"

    fun label(p: String): String = when (p) {
        "claude" -> "Claude"
        "gemini" -> "Gemini"
        else -> "Grok"
    }

    fun defaultModel(p: String): String = when (p) {
        "claude" -> "claude-sonnet-4-5"
        "gemini" -> "gemini-2.5-flash"
        else -> "grok-3"
    }

    fun keyHint(p: String): String = when (p) {
        "claude" -> "کلید را از console.anthropic.com بگیر."
        "gemini" -> "کلید را از aistudio.google.com (بخش Get API key) بگیر."
        else -> "کلید را از console.x.ai بگیر."
    }

    // ---------- تنظیمات ----------

    private fun sp(c: Context) = c.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    fun provider(c: Context): String = sp(c).getString("provider", "gemini") ?: "gemini"

    fun setProvider(c: Context, p: String) {
        sp(c).edit().putString("provider", p).apply()
    }

    fun key(c: Context, p: String): String {
        val v = sp(c).getString("key_$p", "") ?: ""
        if (v.isNotBlank()) return v
        if (p == "claude") {
            return c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("key", "") ?: ""
        }
        return ""
    }

    fun setKey(c: Context, p: String, v: String) {
        sp(c).edit().putString("key_$p", v.trim()).apply()
    }

    fun model(c: Context, p: String): String {
        val m = (sp(c).getString("model_$p", "") ?: "").trim()
        // اصلاح خودکار نام مدل نامعتبر قبلی
        if (m.isBlank() || m == "claude-sonnet-5-5") return defaultModel(p)
        return m
    }

    fun setModel(c: Context, p: String, v: String) {
        sp(c).edit().putString("model_$p", v.trim()).apply()
    }

    fun isReady(c: Context): Boolean = key(c, provider(c)).isNotBlank()

    // استخراج ایمن آرایه غذاها از پاسخ متنی هوش مصنوعی
    fun parseFoodJson(raw: String, dateMs: Long = System.currentTimeMillis()): List<Food> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) throw Exception("قالب پاسخ هوش مصنوعی معتبر نبود")
        val arr = JSONArray(raw.substring(start, end + 1))
        val out = mutableListOf<Food>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            val grams = o.optInt("grams", 0)
            val kcal = o.optDouble("kcal", 0.0).toInt()
            if (name.isNotEmpty() && kcal >= 0) {
                out.add(Food(name = name, grams = grams, kcal = kcal, date = dateMs))
            }
        }
        return out
    }

    // ---------- ارتباط با سرور ----------

    private fun extractErrorMessage(code: Int, text: String): String {
        try {
            val obj = JSONObject(text)
            val err = obj.opt("error")
            if (err is JSONObject) {
                val msg = err.optString("message")
                if (msg.isNotBlank()) return "کد $code: $msg"
            } else if (err is String && err.isNotBlank()) {
                return "کد $code: $err"
            }
        } catch (_: Exception) {
        }
        return "کد $code: ${text.take(250)}"
    }

    private suspend fun http(
        method: String, url: String, headers: Map<String, String>, body: String?
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.setRequestProperty("user-agent", UA)
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception(extractErrorMessage(code, text))
            text
        } finally {
            conn.disconnect()
        }
    }

    // پرسش از ارائه‌دهنده‌ی انتخاب‌شده
    suspend fun ask(c: Context, system: String, user: String): String {
        val p = provider(c)
        val k = key(c, p)
        if (k.isBlank()) throw Exception("کلید API ${label(p)} وارد نشده است")
        val m = model(c, p).removePrefix("models/").trim()
        return when (p) {
            "gemini" -> askGemini(k, m, system, user)
            "grok" -> askGrok(k, m, system, user)
            else -> askClaude(k, m, system, user)
        }
    }

    private suspend fun askClaude(k: String, model: String, system: String, user: String): String {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1500)
            .put("system", system)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", user))
            )
        val text = http(
            "POST", "https://api.anthropic.com/v1/messages",
            mapOf(
                "content-type" to "application/json",
                "x-api-key" to k,
                "anthropic-version" to "2023-06-01"
            ),
            body.toString()
        )
        val arr = JSONObject(text).optJSONArray("content") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("type") == "text") sb.append(o.optString("text"))
        }
        return sb.toString().trim()
    }

    private suspend fun askGemini(k: String, model: String, system: String, user: String): String {
        val body = JSONObject()
        if (system.isNotBlank()) {
            body.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
            )
        }
        body.put(
            "contents",
            JSONArray().put(
                JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", user)))
            )
        )
        val text = http(
            "POST", "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("content-type" to "application/json", "x-goog-api-key" to k),
            body.toString()
        )
        val cands = JSONObject(text).optJSONArray("candidates")
        if (cands == null || cands.length() == 0) throw Exception("پاسخی دریافت نشد: ${text.take(200)}")
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw Exception("پاسخ خالی بود: ${text.take(200)}")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val o = parts.getJSONObject(i)
            if (!o.optBoolean("thought", false)) sb.append(o.optString("text"))
        }
        return sb.toString().trim()
    }

    private suspend fun askGrok(k: String, model: String, system: String, user: String): String {
        val msgs = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject().put("model", model).put("messages", msgs)
        val text = http(
            "POST", "https://api.x.ai/v1/chat/completions",
            mapOf("content-type" to "application/json", "authorization" to "Bearer $k"),
            body.toString()
        )
        val choices = JSONObject(text).optJSONArray("choices")
        if (choices == null || choices.length() == 0) throw Exception("پاسخی دریافت نشد: ${text.take(200)}")
        return choices.getJSONObject(0).getJSONObject("message").optString("content").trim()
    }

    // فهرست مدل‌های قابل استفاده با کلید ذخیره‌شده
    suspend fun listModels(c: Context): List<String> {
        val p = provider(c)
        val k = key(c, p)
        if (k.isBlank()) throw Exception("اول کلید را وارد و ذخیره کن")
        return when (p) {
            "gemini" -> {
                val t = http(
                    "GET", "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
                    mapOf("x-goog-api-key" to k), null
                )
                val arr = JSONObject(t).optJSONArray("models") ?: JSONArray()
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .filter { m ->
                        val g = m.optJSONArray("supportedGenerationMethods")
                        g != null && (0 until g.length()).any { g.getString(it) == "generateContent" }
                    }
                    .map { it.getString("name").removePrefix("models/") }
                    .filter { n ->
                        n.contains("gemini") && listOf("embed", "tts", "image", "live", "audio")
                            .none { n.contains(it) }
                    }
            }
            "grok" -> {
                val t = http(
                    "GET", "https://api.x.ai/v1/models",
                    mapOf("authorization" to "Bearer $k"), null
                )
                val arr = JSONObject(t).optJSONArray("data") ?: JSONArray()
                (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
                    .filter { !it.contains("image") }
            }
            else -> {
                val t = http(
                    "GET", "https://api.anthropic.com/v1/models?limit=100",
                    mapOf("x-api-key" to k, "anthropic-version" to "2023-06-01"), null
                )
                val arr = JSONObject(t).optJSONArray("data") ?: JSONArray()
                (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
            }
        }
    }
}
