package com.ndrimeda

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val id: Long, val username: String, val recipient: String?, val text: String, val createdAt: String)
data class RemoteMessage(val id: Long, val sender: String, val recipient: String, val text: String, val createdAt: String)

object NdrimedaApi {
    private fun call(base: String, path: String, method: String, body: JSONObject? = null, token: String? = null): String {
        val root = base.trim().trimEnd('/')
        require(root.startsWith("https://")) { "Укажи HTTPS-адрес API." }
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
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
            ChatMessage(item.optLong("id"), item.optString("username"), item.optString("recipient").takeIf { it.isNotBlank() && it != "null" }, item.optString("text"), item.optString("created_at"))
        }
    }

    fun inbox(base: String, token: String): List<RemoteMessage> {
        val array = JSONArray(call(base, "/inbox?limit=100", "GET", token = token))
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            RemoteMessage(item.optLong("id"), item.optString("sender"), item.optString("recipient"),
                item.optString("text"), item.optString("created_at"))
        }
    }

    fun sendTo(base: String, token: String, recipient: String, text: String) {
        call(base, "/messages", "POST", JSONObject().put("recipient_username", recipient).put("text", text), token)
    }
}
