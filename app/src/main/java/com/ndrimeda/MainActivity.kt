package com.ndrimeda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NdrimedaScreen() }
    }
}

@Composable
private fun NdrimedaScreen() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("ndrimeda_beta", android.content.Context.MODE_PRIVATE)
    }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var nameDraft by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Offline beta build") }
    var messages by remember {
        mutableStateOf(
            runCatching {
                val stored = JSONArray(prefs.getString("messages", "[]") ?: "[]")
                (0 until stored.length()).map { index ->
                    val item = stored.getJSONObject(index)
                    ChatMessage(
                        item.optString("username"),
                        item.optString("text"),
                        item.optString("created_at")
                    )
                }
            }.getOrDefault(emptyList())
        )
    }

    fun saveMessages(updated: List<ChatMessage>) {
        messages = updated
        val array = JSONArray()
        updated.takeLast(300).forEach { message ->
            array.put(
                JSONObject()
                    .put("username", message.username)
                    .put("text", message.text)
                    .put("created_at", message.createdAt)
            )
        }
        prefs.edit().putString("messages", array.toString()).apply()
    }

    MaterialTheme {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("NDRIMEDA", style = MaterialTheme.typography.headlineMedium)
            Text("CLOSED BETA · OFFLINE MODE", style = MaterialTheme.typography.labelMedium)
            HorizontalDivider()

            if (username.isBlank()) {
                Text("Welcome to the NDRIMEDA beta", style = MaterialTheme.typography.titleLarge)
                Text("Choose a display name to try the app. No account or server is required.")
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(24) },
                    label = { Text("Display name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        val chosen = nameDraft.trim()
                        if (chosen.length < 2) {
                            status = "Enter at least 2 characters."
                        } else {
                            username = chosen
                            prefs.edit().putString("username", chosen).apply()
                            status = "You're in the offline beta."
                        }
                    },
                    enabled = nameDraft.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Enter beta") }
                Spacer(Modifier.weight(1f))
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Local feed", style = MaterialTheme.typography.titleLarge)
                        Text("Signed in as $username", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        username = ""
                        nameDraft = ""
                        prefs.edit().remove("username").apply()
                        status = "Choose a display name to continue."
                    }) { Text("Log out") }
                }

                if (messages.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Your beta starts here", style = MaterialTheme.typography.titleMedium)
                            Text("Send a test message below. In this build, messages stay on this device and are not visible to other testers.")
                        }
                    }
                }

                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(message.username, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(3.dp))
                                Text(message.text)
                                Spacer(Modifier.height(3.dp))
                                Text(message.createdAt, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(2000) },
                    label = { Text("Test message") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            saveMessages(messages + ChatMessage(username, text, time))
                            draft = ""
                            status = "Saved on this device."
                        }
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Send") }
            }

            Text(
                "Offline beta: messages are stored only on this device. Online accounts, shared chats and server sync are not enabled yet.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
