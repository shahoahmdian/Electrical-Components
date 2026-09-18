package com.example.partfinder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Part(
    val name: String, val category: String, val description: String,
    val specs: List<String>, val application: String, val pinout: String, val keywords: String,
    val imageUrl: String = "", val link: String = "", val datasheet: String = ""
)

object Db {
    fun load(ctx: Context): List<Part> {
        val text = ctx.assets.open("components.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val s = o.getJSONArray("specs")
            Part(
                o.getString("name"), o.getString("category"), o.getString("description"),
                (0 until s.length()).map { s.getString(it) },
                o.getString("application"), o.getString("pinout"), o.getString("keywords")
            )
        }
    }

    fun search(all: List<Part>, q: String): List<Part> {
        val tokens = q.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
        if (tokens.isEmpty()) return all
        return all.map { p ->
            val h = (p.name + " " + p.category + " " + p.keywords + " " + p.description).lowercase()
            p to tokens.count { h.contains(it) }
        }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
}

object Vision {
    private const val PROMPT =
        "This is a photo of an electronic component. Read any markings and identify it. " +
        "Reply with ONLY a JSON object: {\"part_number\":\"\",\"type\":\"\",\"markings\":\"\"}. " +
        "Use English. Leave a field empty if unknown."

    fun prepare(f: File): ByteArray {
        val bmp = BitmapFactory.decodeFile(f.absolutePath)
        val scale = 1024f / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        return bos.toByteArray()
    }

    fun identify(apiKey: String, jpeg: ByteArray): String {
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val content = JSONArray()
            .put(JSONObject().put("type", "image").put("source",
                JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64)))
            .put(JSONObject().put("type", "text").put("text", PROMPT))
        val body = JSONObject().put("model", "claude-sonnet-5").put("max_tokens", 300)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("content-type", "application/json")
        c.setRequestProperty("x-api-key", apiKey)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val resp = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) error("HTTP $code")
        return JSONObject(resp).getJSONArray("content").getJSONObject(0).getString("text")
    }
}

object DigiKey {
    private var token = ""
    private var expiresAt = 0L

    private fun post(url: String, headers: Map<String, String>, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val resp = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) error("DigiKey HTTP $code")
        return resp
    }

    private fun auth(id: String, secret: String): String {
        if (token.isNotEmpty() && System.currentTimeMillis() < expiresAt) return token
        val body = "client_id=" + java.net.URLEncoder.encode(id, "UTF-8") +
            "&client_secret=" + java.net.URLEncoder.encode(secret, "UTF-8") + "&grant_type=client_credentials"
        val r = JSONObject(post("https://api.digikey.com/v1/oauth2/token",
            mapOf("Content-Type" to "application/x-www-form-urlencoded"), body))
        token = r.getString("access_token")
        expiresAt = System.currentTimeMillis() + (r.optLong("expires_in", 500) - 30) * 1000
        return token
    }

    fun search(id: String, secret: String, q: String): List<Part> {
        val t = auth(id, secret)
        val body = JSONObject().put("Keywords", q).put("Limit", 15).toString()
        val resp = JSONObject(post("https://api.digikey.com/products/v4/search/keyword", mapOf(
            "Authorization" to "Bearer $t", "X-DIGIKEY-Client-Id" to id,
            "X-DIGIKEY-Locale-Site" to "US", "X-DIGIKEY-Locale-Language" to "en",
            "X-DIGIKEY-Locale-Currency" to "USD", "Content-Type" to "application/json"), body))
        val arr = resp.optJSONArray("Products") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            val d = p.optJSONObject("Description")
            val specs = mutableListOf<String>()
            p.optJSONObject("Manufacturer")?.optString("Name")?.takeIf { it.isNotEmpty() }?.let { specs += "سازنده: $it" }
            if (p.has("UnitPrice")) specs += "قیمت واحد: ${p.optDouble("UnitPrice")} USD"
            if (p.has("QuantityAvailable")) specs += "موجودی: ${p.optInt("QuantityAvailable")}"
            val params = p.optJSONArray("Parameters")
            if (params != null) for (j in 0 until minOf(params.length(), 30)) {
                val o = params.getJSONObject(j)
                specs += o.optString("ParameterText") + ": " + o.optString("ValueText")
            }
            Part(
                name = p.optString("ManufacturerProductNumber"),
                category = p.optJSONObject("Category")?.optString("Name") ?: "",
                description = d?.optString("DetailedDescription")?.ifEmpty { d.optString("ProductDescription") } ?: "",
                specs = specs, application = "", pinout = "", keywords = "",
                imageUrl = p.optString("PhotoUrl"), link = p.optString("ProductUrl"),
                datasheet = p.optString("DatasheetUrl")
            )
        }
    }
}
