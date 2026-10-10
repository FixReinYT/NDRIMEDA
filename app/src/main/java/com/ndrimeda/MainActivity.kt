package com.ndrimeda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NdrimedaScreen() }
    }
}

@Composable
private fun NdrimedaScreen() {
    var username by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("https://YOUR-SERVER.example") }
    var status by remember { mutableStateOf("Backend connection is not configured yet.") }
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("NDRIMEDA", style = MaterialTheme.typography.headlineLarge)
            Text("Native Android messenger", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(server, { server = it }, label = { Text("Backend HTTPS URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { status = "API connection will be enabled in the next build." }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}