package com.ndrimeda

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val username: String, val text: String, val createdAt: String)

object NdrimedaApi {
    private fun call(base: String, path: String, method: String, body: JSONObject? = null, token: String? = null): String {
        val root = base.trim().trimEnd('/')
        require(root.startsWith("https://")) { "Enter the HTTPS URL of your deployed API." }
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrDefault("")
                error(detail.ifBlank { "HTTP $code" })
            }
            return text
        } finally { connection.disconnect() }
    }

    fun authenticate(base: String, username: String, password: String, register: Boolean): JSONObject =
        JSONObject(call(base, if (register) "/auth/register" else "/auth/login", "POST",
            JSONObject().put("username", username).put("password", password)))

    fun messages(base: String, token: String): List<ChatMessage> {
        val array = JSONArray(call(base, "/messages?limit=100", "GET", token = token))
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            ChatMessage(item.optString("username"), item.optString("text"), item.optString("created_at"))
        }
    }

    fun send(base: String, token: String, text: String) {
        call(base, "/messages", "POST", JSONObject().put("text", text), token)
    }
}