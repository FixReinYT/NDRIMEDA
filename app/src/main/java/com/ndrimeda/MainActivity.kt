package com.ndrimeda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF202532)
private val Muted = Color(0xFF858B98)
private val Accent = Color(0xFF5267D9)
private val Canvas = Color(0xFFF7F8FC)
private val Line = Color(0xFFE9EBF2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NdrimedaApp() }
    }
}

private data class Chat(
    val id: String,
    val name: String,
    val preview: String,
    val time: String,
    val initials: String,
    val color: Color,
    val unread: Int = 0,
    val online: Boolean = false
)

private data class Message(val author: String, val text: String, val time: String, val mine: Boolean)

private val starterChats = listOf(
    Chat("alex", "Алексей", "Скинул тебе файл", "12:42", "А", Color(0xFFDCE6FF), 2, true),
    Chat("masha", "Маша", "Ты сегодня будешь?", "12:18", "М", Color(0xFFFFE3E8), 1, true),
    Chat("team", "Команда NDRIMEDA", "Илья: сборка готова", "11:56", "N", Color(0xFFDDF4E8), 3),
    Chat("danya", "Даня", "ахах, понял", "Вчера", "Д", Color(0xFFFFE9CE)),
    Chat("saved", "Избранное", "Заметки и сообщения для себя", "Пн", "★", Color(0xFFE7E4FF))
)

@Composable
private fun NdrimedaApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("ndrimeda_beta", android.content.Context.MODE_PRIVATE)
    }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var nameDraft by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var currentChat by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("chats") }
    var status by remember { mutableStateOf("") }
    var localMessages by remember {
        mutableStateOf(
            runCatching {
                val array = JSONArray(prefs.getString("messages", "[]") ?: "[]")
                (0 until array.length()).map { i ->
                    val item = array.getJSONObject(i)
                    item.optString("chatId") to Message(
                        item.optString("author"), item.optString("text"),
                        item.optString("time"), item.optBoolean("mine")
                    )
                }.groupBy({ it.first }, { it.second })
            }.getOrDefault(emptyMap())
        )
    }

    val chats = starterChats.filter {
        query.isBlank() || it.name.contains(query, true) || it.preview.contains(query, true)
    }
    val selected = starterChats.firstOrNull { it.id == currentChat }

    fun persistMessages(updated: Map<String, List<Message>>) {
        localMessages = updated
        val array = JSONArray()
        updated.forEach { (chatId, messages) ->
            messages.takeLast(200).forEach { message ->
                array.put(JSONObject()
                    .put("chatId", chatId)
                    .put("author", message.author)
                    .put("text", message.text)
                    .put("time", message.time)
                    .put("mine", message.mine))
            }
        }
        prefs.edit().putString("messages", array.toString()).apply()
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = Canvas,
            surface = Color.White,
            onSurface = Ink,
            onBackground = Ink
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            if (username.isBlank()) {
                WelcomeScreen(
                    name = nameDraft,
                    onNameChange = { nameDraft = it.take(24) },
                    onContinue = {
                        username = nameDraft.trim()
                        prefs.edit().putString("username", username).apply()
                    }
                )
            } else if (currentChat != null && selected != null) {
                ConversationScreen(
                    chat = selected,
                    username = username,
                    messages = localMessages[selected.id].orEmpty(),
                    draft = draft,
                    onDraftChange = { draft = it.take(2000) },
                    onBack = { currentChat = null; draft = "" },
                    onSend = {
                        val text = draft.trim()
                        if (text.isNotEmpty()) {
                            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            persistMessages(localMessages + (selected.id to (localMessages[selected.id].orEmpty() + Message(username, text, time, true))))
                            draft = ""
                        }
                    }
                )
            } else {
                Scaffold(
                    containerColor = Canvas,
                    bottomBar = {
                        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                            NavigationBarItem(
                                selected = tab == "chats",
                                onClick = { tab = "chats" },
                                icon = { Text("▤", fontSize = 21.sp) },
                                label = { Text("Чаты") }
                            )
                            NavigationBarItem(
                                selected = tab == "contacts",
                                onClick = { tab = "contacts" },
                                icon = { Text("♙", fontSize = 21.sp) },
                                label = { Text("Контакты") }
                            )
                            NavigationBarItem(
                                selected = tab == "profile",
                                onClick = { tab = "profile" },
                                icon = { Text("◉", fontSize = 21.sp) },
                                label = { Text("Профиль") }
                            )
                        }
                    }
                ) { padding ->
                    when (tab) {
                        "profile" -> ProfileScreen(
                            username = username,
                            onLogout = {
                                username = ""
                                nameDraft = ""
                                tab = "chats"
                                prefs.edit().remove("username").apply()
                            }
                        )
                        "contacts" -> ContactsScreen(onOpenChat = { currentChat = it; tab = "chats" })
                        else -> Column(
                            Modifier.fillMaxSize().padding(padding)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("NDRIMEDA", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                    Text("Чаты", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
                                }
                                HeaderAction("＋") { status = "Создание нового чата появится в онлайн-версии." }
                            }
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                placeholder = { Text("Поиск по чатам", color = Muted) },
                                leadingIcon = { Text("⌕", fontSize = 25.sp, color = Muted) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Line,
                                    focusedBorderColor = Accent,
                                    unfocusedContainerColor = Color.White,
                                    focusedContainerColor = Color.White
                                )
                            )
                            Text(
                                "НЕДАВНИЕ",
                                modifier = Modifier.padding(start = 22.dp, top = 10.dp, bottom = 10.dp),
                                color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                            )
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(chats, key = { it.id }) { chat ->
                                    ChatRow(chat = chat, onClick = { currentChat = chat.id })
                                }
                                if (chats.isEmpty()) {
                                    item {
                                        Text("Ничего не найдено", color = Muted, modifier = Modifier.padding(24.dp))
                                    }
                                }
                                if (status.isNotBlank()) {
                                    item {
                                        Text(status, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                                    }
                                }
                                item {
                                    Text(
                                        "Бета-режим · переписки пока хранятся на этом устройстве",
                                        color = Muted, fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(name: String, onNameChange: (String) -> Unit, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Canvas).padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(66.dp).clip(RoundedCornerShape(22.dp)).background(Accent),
            contentAlignment = Alignment.Center
        ) { Text("N", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(28.dp))
        Text("NDRIMEDA", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text("Общение без лишнего.", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
        Spacer(Modifier.height(10.dp))
        Text("Выбери имя и посмотри, как устроена бета-версия мессенджера.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Твоё имя") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Accent, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onContinue,
            enabled = name.trim().length >= 2,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Продолжить", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(18.dp))
        Text("ЗАКРЫТАЯ БЕТА · ОФЛАЙН", color = Muted, fontSize = 10.sp, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun ChatRow(chat: Chat, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(chat.color),
                    contentAlignment = Alignment.Center
                ) { Text(chat.initials, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                if (chat.online) {
                    Box(Modifier.align(Alignment.BottomEnd).size(13.dp).clip(CircleShape).background(Color.White).padding(2.dp)) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF39B980)))
                    }
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(chat.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(chat.preview, fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(chat.time, fontSize = 11.sp, color = Muted)
                if (chat.unread > 0) {
                    Box(Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape).background(Accent).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                        Text(chat.unread.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        HorizontalDivider(color = Line, thickness = 0.7.dp, modifier = Modifier.padding(start = 87.dp))
    }
}

@Composable
private fun ConversationScreen(
    chat: Chat,
    username: String,
    messages: List<Message>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Canvas)) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 10.dp), fontSize = 32.sp, color = Accent)
            Box(Modifier.size(42.dp).clip(CircleShape).background(chat.color), contentAlignment = Alignment.Center) {
                Text(chat.initials, fontWeight = FontWeight.SemiBold, color = Ink)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(chat.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                Text(if (chat.online) "в сети" else "демо-чат", fontSize = 12.sp, color = if (chat.online) Color(0xFF2C9A70) else Muted)
            }
            Text("⋮", color = Muted, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 10.dp))
        }
        Text(
            "Это демонстрационная переписка. Другие пользователи её не видят.",
            modifier = Modifier.fillMaxWidth().background(Color(0xFFEEF0FF)).padding(horizontal = 16.dp, vertical = 9.dp),
            color = Accent, fontSize = 11.sp, lineHeight = 15.sp
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            reverseLayout = false
        ) {
            val shownMessages = if (messages.isNotEmpty()) messages else listOf(
                Message(chat.name, chat.preview, chat.time, false)
            )
            items(shownMessages) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 290.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (message.mine) Accent else Color.White)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (!message.mine) Text(message.author, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(message.text, color = if (message.mine) Color.White else Ink, fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(message.time, color = if (message.mine) Color(0xFFDDE2FF) else Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение", color = Muted) },
                shape = RoundedCornerShape(24.dp),
                singleLine = false,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Accent, unfocusedContainerColor = Canvas, focusedContainerColor = Canvas)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSend, enabled = draft.isNotBlank(), contentPadding = PaddingValues(0.dp), modifier = Modifier.size(48.dp), shape = CircleShape) {
                Text("➤", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun ContactsScreen(onOpenChat: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp)) {
        Text("Контакты", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("Знакомые из демо-списка", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        starterChats.filter { it.id != "saved" }.forEach { chat ->
            ChatRow(chat.copy(preview = "Открыть переписку"), onClick = { onOpenChat(chat.id) })
        }
    }
}

@Composable
private fun ProfileScreen(username: String, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Профиль", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(76.dp).clip(CircleShape).background(Color(0xFFE3E7FF)), contentAlignment = Alignment.Center) {
            Text(username.take(1).uppercase(), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Accent)
        }
        Spacer(Modifier.height(14.dp))
        Text(username, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text("Локальный профиль бета-тестера", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(16.dp))
        Text("Версия", color = Muted, fontSize = 12.sp)
        Text("NDRIMEDA Beta 0.1.0", color = Ink, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        Text("Режим", color = Muted, fontSize = 12.sp)
        Text("Офлайн · данные на устройстве", color = Ink, fontSize = 15.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("Сменить профиль")
        }
    }
}

@Composable
private fun HeaderAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Color.White).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 25.sp, color = Accent) }
}
