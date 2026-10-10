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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NdrimedaScreen() }
    }
}

@Composable
private fun NdrimedaScreen() {
    var server by remember { mutableStateOf("https://YOUR-SERVER.example") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
    var status by remember { mutableStateOf("Set your API HTTPS URL.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh(auth: String) {
        scope.launch {
            busy = true
            try {
                messages = withContext(Dispatchers.IO) { NdrimedaApi.messages(server, auth) }
                status = "Messages refreshed."
            } catch (e: Exception) { status = e.message ?: "Request failed." }
            busy = false
        }
    }

    fun auth(register: Boolean) {
        if (username.trim().length < 3 || password.length < 8) {
            status = "Username needs 3+ characters; password needs 8+."
            return
        }
        scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) {
                    NdrimedaApi.authenticate(server, username.trim(), password, register)
                }
                val authToken = result.getString("access_token")
                token = authToken
                name = result.getJSONObject("user").getString("username")
                messages = withContext(Dispatchers.IO) { NdrimedaApi.messages(server, authToken) }
                status = "Connected."
            } catch (e: Exception) { status = e.message ?: "Login failed." }
            busy = false
        }
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("NDRIMEDA", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(server, { server = it }, label = { Text("Backend HTTPS URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (token == null) {
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("Password (8+ characters)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { auth(true) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Register") }
                    Button(onClick = { auth(false) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Log in") }
                }
            } else {
                Text("Signed in as $name")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { refresh(token!!) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Refresh") }
                    OutlinedButton(onClick = { token = null; messages = emptyList() }, modifier = Modifier.weight(1f)) { Text("Log out") }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(messages) { m ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(m.username, style = MaterialTheme.typography.labelLarge)
                                Text(m.text)
                                Text(m.createdAt, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                OutlinedTextField(draft, { draft = it }, label = { Text("Message to shared feed") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) scope.launch {
                        busy = true
                        try {
                            withContext(Dispatchers.IO) { NdrimedaApi.send(server, token!!, text) }
                            draft = ""
                            messages = withContext(Dispatchers.IO) { NdrimedaApi.messages(server, token!!) }
                            status = "Message sent."
                        } catch (e: Exception) { status = e.message ?: "Send failed." }
                        busy = false
                    }
                }, enabled = !busy && draft.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Send") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, style = MaterialTheme.typography.bodySmall)
            Text("Development version: the feed is shared, not private chats.", style = MaterialTheme.typography.bodySmall)
        }
    }
}