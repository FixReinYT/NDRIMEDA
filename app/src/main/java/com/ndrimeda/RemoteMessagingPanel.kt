package com.ndrimeda

import android.content.SharedPreferences
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val DEFAULT_RELAY = "https://ndrimeda-api.onrender.com"

private data class NetworkLine(
    val key: String,
    val sender: String,
    val recipient: String,
    val text: String,
    val createdAt: String,
    val outgoing: Boolean
)

@Composable
fun RemoteMessagingPanel(
    dark: Boolean,
    accent: Color,
    ink: Color,
    muted: Color,
    panel: Color,
    prefs: SharedPreferences,
    localUsername: String
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var relay by remember { mutableStateOf(prefs.getString("relay_url", DEFAULT_RELAY) ?: DEFAULT_RELAY) }
    var account by remember { mutableStateOf(prefs.getString("username", localUsername) ?: localUsername) }
    var password by remember { mutableStateOf("") }
    var registerMode by remember { mutableStateOf(true) }
    var token by remember { mutableStateOf(prefs.getString("auth_token", "") ?: "") }
    var recipient by remember { mutableStateOf(prefs.getString("network_recipient", "") ?: "") }
    var draft by remember { mutableStateOf(prefs.getString("network_draft", "") ?: "") }
    var timeline by remember { mutableStateOf<List<NetworkLine>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (token.isBlank()) "Создай аккаунт или войди, чтобы общаться с людьми." else "Подключаюсь к серверу…") }

    fun refresh() {
        if (token.isBlank()) {
            status = "Сначала войди в аккаунт."
            return
        }
        if (busy) return
        busy = true
        status = "Обновляю переписку…"
        Thread {
            try {
                val sentMessages = NdrimedaApi.messages(relay, token)
                val receivedMessages = NdrimedaApi.inbox(relay, token)
                val lines = sentMessages.map {
                    NetworkLine("sent_${it.id}", it.username, it.recipient ?: "общая лента", it.text, it.createdAt, true)
                } + receivedMessages.map {
                    NetworkLine("received_${it.id}", it.sender, account, it.text, it.createdAt, false)
                }
                mainHandler.post {
                    timeline = lines.sortedBy { it.createdAt }.takeLast(200)
                    status = "Синхронизация завершена · сообщений: ${lines.size}"
                    busy = false
                }
            } catch (e: Exception) {
                mainHandler.post {
                    status = e.message?.take(180) ?: "Не удалось подключиться. Проверь интернет."
                    busy = false
                }
            }
        }.start()
    }

    LaunchedEffect(token, relay) {
        if (token.isNotBlank()) refresh()
    }

    Column(
        Modifier.fillMaxSize()
            .background(if (dark) Color(0xFF11141B) else Color(0xFFF7F8FC))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Личные сообщения", color = ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Твой юзернейм: @${account.ifBlank { localUsername }}", color = accent, fontWeight = FontWeight.SemiBold)
        Text(
            "Чтобы писать на расстоянии, у обоих пользователей должен быть аккаунт на одном сервере и интернет. Без интернета сообщения не доставляются; черновик сохранится на телефоне.",
            color = muted, fontSize = 12.sp
        )

        if (token.isBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value = relay,
                        onValueChange = { relay = it.take(240) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("HTTPS-сервер") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = account,
                        onValueChange = { account = it.filter { c -> c.isLetterOrDigit() || c in "_.-" }.take(32) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Выбери юзернейм") },
                        supportingText = { Text("3–32 символа: латиница, цифры, точка, дефис или _") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(128) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Пароль (от 8 символов)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = registerMode, onClick = { registerMode = true }, label = { Text("Создать аккаунт") })
                        FilterChip(selected = !registerMode, onClick = { registerMode = false }, label = { Text("Войти") })
                    }
                    Button(
                        onClick = {
                            if (account.trim().length !in 3..32 || password.length < 8 || !relay.startsWith("https://")) {
                                status = "Проверь HTTPS-сервер, юзернейм и пароль."
                            } else if (!busy) {
                                busy = true
                                status = if (registerMode) "Создаю аккаунт…" else "Вхожу…"
                                Thread {
                                    try {
                                        val result = NdrimedaApi.authenticate(relay, account.trim(), password, registerMode)
                                        val newToken = result.optString("access_token")
                                        if (newToken.isBlank()) error("Сервер не вернул токен входа.")
                                        val canonicalName = result.optJSONObject("user")?.optString("username").orEmpty().ifBlank { account.trim() }
                                        mainHandler.post {
                                            account = canonicalName
                                            token = newToken
                                            prefs.edit()
                                                .putString("relay_url", relay.trim().trimEnd('/'))
                                                .putString("username", canonicalName)
                                                .putString("auth_token", newToken)
                                                .apply()
                                            password = ""
                                            status = "Аккаунт подключён."
                                            busy = false
                                        }
                                    } catch (e: Exception) {
                                        mainHandler.post {
                                            status = e.message?.take(180) ?: "Ошибка подключения."
                                            busy = false
                                        }
                                    }
                                }.start()
                            }
                        },
                        enabled = !busy && account.trim().length in 3..32 && password.length >= 8 && relay.startsWith("https://"),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Подожди…" else if (registerMode) "Зарегистрироваться" else "Войти") }
                }
            }
        } else {
            OutlinedTextField(
                value = recipient,
                onValueChange = {
                    recipient = it.filter { c -> c.isLetterOrDigit() || c in "_.-" }.take(32)
                    prefs.edit().putString("network_recipient", recipient).apply()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Кому написать — юзернейм") },
                placeholder = { Text("Например, Lucas_123") },
                singleLine = true
            )
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it.take(4000)
                    prefs.edit().putString("network_draft", draft).apply()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Сообщение") },
                placeholder = { Text("Напиши сообщение…") },
                minLines = 2,
                maxLines = 5
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val to = recipient.trim()
                        val body = draft.trim()
                        when {
                            to.length !in 3..32 -> status = "Введи юзернейм получателя (3–32 символа)."
                            to.equals(account, ignoreCase = true) -> status = "Нельзя написать самому себе."
                            body.isBlank() -> status = "Напиши текст сообщения."
                            busy -> Unit
                            else -> {
                                busy = true
                                status = "Отправляю сообщение…"
                                Thread {
                                    try {
                                        NdrimedaApi.sendTo(relay, token, to, body)
                                        mainHandler.post {
                                            draft = ""
                                            prefs.edit().remove("network_draft").apply()
                                            status = "Сообщение отправлено пользователю @${to}."
                                            busy = false
                                            refresh()
                                        }
                                    } catch (e: Exception) {
                                        mainHandler.post {
                                            status = e.message?.take(180) ?: "Не удалось отправить. Черновик сохранён."
                                            busy = false
                                        }
                                    }
                                }.start()
                            }
                        }
                    },
                    enabled = !busy && recipient.trim().length in 3..32 && draft.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text(if (busy) "Отправка…" else "Отправить") }
                OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text("Обновить") }
            }
            OutlinedButton(
                onClick = {
                    prefs.edit().remove("auth_token").apply()
                    token = ""
                    timeline = emptyList()
                    status = "Выполнен выход с сервера."
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Выйти с сервера") }
        }

        Text(status, color = muted, fontSize = 12.sp)
        HorizontalDivider(color = if (dark) Color(0xFF303746) else Color(0xFFE9EBF2))
        Text("История переписки", color = ink, fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(timeline, key = { it.key }) { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.outgoing) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 310.dp)
                            .background(if (item.outgoing) accent else panel, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            if (item.outgoing) "Ты → @${item.recipient}" else "@${item.sender} → тебе",
                            color = if (item.outgoing) Color.White else accent,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(item.text, color = if (item.outgoing) Color.White else ink)
                        Spacer(Modifier.height(4.dp))
                        Text(item.createdAt.replace("T", " ").take(16), color = if (item.outgoing) Color(0xFFE0E4FF) else muted, fontSize = 10.sp)
                    }
                }
            }
            if (timeline.isEmpty()) item {
                Text("Сообщений пока нет. Напиши человеку по юзернейму и нажми «Отправить».", color = muted, modifier = Modifier.padding(vertical = 20.dp))
            }
        }
    }
}
