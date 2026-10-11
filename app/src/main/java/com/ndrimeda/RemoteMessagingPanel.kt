package com.ndrimeda

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val DEFAULT_RELAY = "https://ndrimeda-api.onrender.com"

@Composable
fun RemoteMessagingPanel(
    dark: Boolean,
    accent: Color,
    ink: Color,
    muted: Color,
    panel: Color,
    prefs: android.content.SharedPreferences,
    localUsername: String
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var relay by remember { mutableStateOf(prefs.getString("relay_url", DEFAULT_RELAY) ?: DEFAULT_RELAY) }
    var account by remember { mutableStateOf(prefs.getString("username", localUsername) ?: localUsername) }
    var password by remember { mutableStateOf("") }
    var registerMode by remember { mutableStateOf(true) }
    var token by remember { mutableStateOf(prefs.getString("auth_token", "") ?: "") }
    var recipient by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var inbox by remember { mutableStateOf<List<RemoteMessage>>(emptyList()) }
    var sent by remember { mutableStateOf<List<RemoteMessage>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (token.isBlank()) "Войди или создай аккаунт на выбранном узле." else "Подключение не проверено. Обнови входящие.") }

    fun runNetwork(action: () -> Unit) {
        if (busy) return
        busy = true
        status = "Подключение…"
        Thread {
            try {
                action()
            } catch (e: Exception) {
                mainHandler.post { status = e.message?.take(180) ?: "Ошибка соединения"; busy = false }
            }
        }.start()
    }

    fun refreshInbox() {
        if (token.isBlank()) { status = "Сначала войди на узел."; return }
        runNetwork {
            val fetched = NdrimedaApi.inbox(relay, token)
            mainHandler.post {
                inbox = fetched
                status = "Подключено к узлу. Входящих: ${fetched.size}"
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(if (dark) Color(0xFF11141B) else Color(0xFFF7F8FC)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Сообщения через интернет", color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Прямые сообщения по логину через relay-узел. Не Bluetooth.", color = muted, fontSize = 13.sp)
        }

        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Узел подключения", color = ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = relay, onValueChange = { relay = it.take(240) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("HTTPS-адрес узла") },
                    singleLine = true, shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = account, onValueChange = { account = it.filter { c -> c.isLetterOrDigit() || c in "_.-" }.take(32) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Твой логин") },
                    singleLine = true, shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it.take(128) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Пароль") },
                    singleLine = true, shape = RoundedCornerShape(14.dp),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = registerMode, onClick = { registerMode = true }, label = { Text("Регистрация") })
                    FilterChip(selected = !registerMode, onClick = { registerMode = false }, label = { Text("Вход") })
                }
                Button(
                    onClick = {
                        if (account.trim().length !in 3..32 || password.length < 8) {
                            status = "Логин: 3–32 символа. Пароль: минимум 8 символов."; return@Button
                        }
                        runNetwork {
                            val result = NdrimedaApi.authenticate(relay, account.trim(), password, registerMode)
                            val newToken = result.optString("access_token")
                            if (newToken.isBlank()) error("Узел не вернул токен входа")
                            mainHandler.post {
                                token = newToken
                                account = result.optJSONObject("user")?.optString("username", account) ?: account
                                prefs.edit().putString("relay_url", relay.trim().trimEnd('/'))
                                    .putString("username", account).putString("auth_token", newToken).apply()
                                password = ""
                                status = "Вход выполнен. Теперь можно отправлять сообщения."
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && relay.startsWith("https://") && password.length >= 8,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                ) { Text(if (registerMode) "Создать аккаунт на узле" else "Войти на узел") }
            }
        }

        if (token.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Новое сообщение", color = ink, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = recipient, onValueChange = { recipient = it.filter { c -> c.isLetterOrDigit() || c in "_.-" }.take(32) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Логин получателя") },
                        singleLine = true, shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it.take(4000) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Сообщение") },
                        minLines = 2, maxLines = 5, shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = {
                            val to = recipient.trim()
                            val body = draft.trim()
                            if (to.length < 3 || body.isBlank()) { status = "Укажи логин получателя и текст."; return@Button }
                            runNetwork {
                                NdrimedaApi.sendTo(relay, token, to, body)
                                val item = RemoteMessage(System.currentTimeMillis(), account, to, body, "только что")
                                mainHandler.post {
                                    sent = (sent + item).takeLast(100)
                                    draft = ""
                                    status = "Отправлено на узел для доставки пользователю @$to."
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && recipient.trim().length >= 3 && draft.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    ) { Text("Отправить через интернет") }
                    OutlinedButton(
                        onClick = { refreshInbox() }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    ) { Text("Обновить входящие") }
                }
            }
        }

        Text(status, color = muted, fontSize = 12.sp)
        Text("Переписка на этом узле", color = ink, fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items((inbox.map { it.copy(text = it.text, recipient = "Вы", sender = it.sender) } + sent).takeLast(100).reversed()) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (item.sender == account) "Вы → @${item.recipient}" else "@${item.sender} → вам", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(item.text, color = ink)
                        Text(item.createdAt, color = muted, fontSize = 10.sp)
                    }
                }
            }
        }
        Text(
            "Ограничение беты: аккаунты и доставка пока существуют в пределах одного узла. Узел хранит текст сообщений; E2EE и федерация между независимыми узлами ещё не реализованы. Не отправляй конфиденциальные данные.",
            color = muted, fontSize = 11.sp
        )
    }
}
