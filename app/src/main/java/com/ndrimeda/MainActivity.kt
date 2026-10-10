package com.ndrimeda

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF5267D9)
private val LightInk = Color(0xFF202532)
private val LightMuted = Color(0xFF858B98)
private val LightLine = Color(0xFFE9EBF2)
private val AvatarColors = listOf(Color(0xFFDCE6FF), Color(0xFFFFE3E8), Color(0xFFDDF4E8), Color(0xFFFFE9CE), Color(0xFFE7E4FF), Color(0xFFD9F2F7))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NdrimedaApp() }
    }
}

private fun createRecorder(context: Context, path: String): MediaRecorder {
    @Suppress("DEPRECATION")
    val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
    r.setAudioSource(MediaRecorder.AudioSource.MIC)
    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    r.setAudioEncodingBitRate(96000)
    r.setAudioSamplingRate(44100)
    r.setOutputFile(path)
    r.prepare()
    r.start()
    return r
}


private fun authRequest(path: String, username: String, password: String): JSONObject {
    val connection = (URL(API_BASE_URL + path).openConnection() as HttpURLConnection)
    connection.requestMethod = "POST"
    connection.connectTimeout = 12000
    connection.readTimeout = 12000
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    val payload = JSONObject().put("username", username).put("password", password).toString()
    connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
    val code = connection.responseCode
    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    connection.disconnect()
    val result = if (body.isBlank()) JSONObject() else JSONObject(body)
    if (code !in 200..299) throw IllegalStateException(result.optString("detail", "Ошибка сервера (" + code + ")"))
    return result
}
private fun stamp() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
private fun makeId() = System.currentTimeMillis().toString() + "_" + (0..999).random()

private data class Chat(
    val id: String, val name: String, val preview: String, val time: String,
    val initials: String, val kind: String = "person", val color: Int = 0,
    val discussionFor: String = ""
)
private data class Message(
    val id: String, val author: String, val text: String, val time: String,
    val mine: Boolean, val voice: String = "", val parentId: String = ""
)
private val Defaults = emptyList<Chat>()

private const val API_BASE_URL = "https://ndrimeda-api.onrender.com"

@Composable
private fun NdrimedaApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ndrimeda_beta", Context.MODE_PRIVATE) }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var nameDraft by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("chats") }
    var currentId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf(prefs.getString("theme", "light") ?: "light") }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf("group") }
    var status by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordPath by remember { mutableStateOf("") }
    var recordChat by remember { mutableStateOf("") }
    var playingId by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var discussionParent by remember { mutableStateOf<Message?>(null) }
    var discussionChatId by remember { mutableStateOf("") }

    var chats by remember {
        mutableStateOf(runCatching {
            val a = JSONArray(prefs.getString("custom_chats", "[]"))
            Defaults + (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Chat(o.getString("id"), o.getString("name"), o.optString("preview", "Новый чат"), o.optString("time", ""), o.optString("initials", "N"), o.optString("kind", "group"), o.optInt("color"), o.optString("discussionFor", ""))
            }
        }.getOrDefault(Defaults))
    }
    var messages by remember {
        mutableStateOf(runCatching {
            val a = JSONArray(prefs.getString("messages", "[]"))
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                o.getString("chat") to Message(o.optString("id", makeId()), o.optString("author"), o.optString("text"), o.optString("time"), o.optBoolean("mine"), o.optString("voice"), o.optString("parentId"))
            }.groupBy({ it.first }, { it.second })
        }.getOrDefault(emptyMap()))
    }

    fun saveChats(list: List<Chat>) {
        chats = list
        val a = JSONArray()
        list.filter { c -> Defaults.none { it.id == c.id } }.forEach { c ->
            a.put(JSONObject().put("id", c.id).put("name", c.name).put("preview", c.preview).put("time", c.time).put("initials", c.initials).put("kind", c.kind).put("color", c.color).put("discussionFor", c.discussionFor))
        }
        prefs.edit().putString("custom_chats", a.toString()).apply()
    }
    fun saveMessages(map: Map<String, List<Message>>) {
        messages = map
        val a = JSONArray()
        map.forEach { (chatId, list) -> list.takeLast(300).forEach { m ->
            a.put(JSONObject().put("chat", chatId).put("id", m.id).put("author", m.author).put("text", m.text).put("time", m.time).put("mine", m.mine).put("voice", m.voice).put("parentId", m.parentId))
        } }
        prefs.edit().putString("messages", a.toString()).apply()
    }

    val dark = theme == "dark"
    val bg = if (dark) Color(0xFF11141B) else Color(0xFFF7F8FC)
    val panel = if (dark) Color(0xFF1B202B) else Color.White
    val ink = if (dark) Color(0xFFF0F2F8) else LightInk
    val muted = if (dark) Color(0xFFA4ABBB) else LightMuted
    val line = if (dark) Color(0xFF303746) else LightLine
    val accent = if (dark) Color(0xFF8B9AFF) else Accent
    val scheme = if (dark) darkColorScheme(primary = accent, background = bg, surface = panel, onSurface = ink, onBackground = ink) else lightColorScheme(primary = accent, background = bg, surface = panel, onSurface = ink, onBackground = ink)
    val selected = chats.firstOrNull { it.id == currentId }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && currentId != null) {
            runCatching {
                val file = File(context.filesDir, "voice_${System.currentTimeMillis()}.m4a")
                recorder = createRecorder(context, file.absolutePath)
                recordPath = file.absolutePath
                recordChat = currentId!!
                recording = true
                status = "Идёт запись. Нажми ■ для отправки."
            }.onFailure { status = "Не удалось включить запись. Проверь доступ к микрофону." }
        } else status = "Разреши доступ к микрофону для голосовых сообщений."
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            if (username.isBlank()) {
                Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(66.dp).clip(RoundedCornerShape(22.dp)).background(accent), contentAlignment = Alignment.Center) { Text("N", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(26.dp))
                    Text("NDRIMEDA", color = accent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Общение без лишнего.", color = ink, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Создавай локальные группы и каналы, записывай голосовые сообщения и настраивай тему.", color = muted)
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(nameDraft, { nameDraft = it.take(24) }, Modifier.fillMaxWidth(), label = { Text("Твоё имя") }, singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { username = nameDraft.trim(); prefs.edit().putString("username", username).apply() }, enabled = nameDraft.trim().length >= 2, modifier = Modifier.fillMaxWidth()) { Text("Продолжить") }
                    Text("ЗАКРЫТАЯ БЕТА · ЛОКАЛЬНЫЙ РЕЖИМ", color = muted, fontSize = 10.sp, modifier = Modifier.padding(top = 16.dp))
                }
            } else if (discussionParent != null) {
                Column(Modifier.fillMaxSize().background(bg)) {
                    Row(Modifier.fillMaxWidth().background(panel).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("‹", Modifier.clickable { discussionParent = null }.padding(end = 18.dp), fontSize = 30.sp, color = accent)
                        Column { Text("Обсуждение", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ink); Text("Комментарии к публикации", color = muted, fontSize = 12.sp) }
                    }
                    Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp)).background(panel).padding(14.dp)) {
                        Text(discussionParent!!.author, color = accent, fontWeight = FontWeight.Bold)
                        Text(discussionParent!!.text, color = ink)
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        items(messages[discussionChatId].orEmpty(), key = { it.id }) { m ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(panel).padding(12.dp)) {
                                Text(m.author, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(m.text, color = ink)
                                Text(m.time, color = muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }
                    var comment by remember { mutableStateOf("") }
                    Row(Modifier.fillMaxWidth().background(panel).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(comment, { comment = it.take(2000) }, Modifier.weight(1f), placeholder = { Text("Написать комментарий…") }, singleLine = true)
                        Button(onClick = { if (comment.isNotBlank()) { val m = Message(makeId(), username, comment.trim(), stamp(), true, parentId = discussionParent!!.id); saveMessages(messages + (discussionChatId to (messages[discussionChatId].orEmpty() + m))); comment = "" } }, enabled = comment.isNotBlank()) { Text("➤") }
                    }
                }
            } else if (selected != null) {
                Column(Modifier.fillMaxSize().background(bg)) {
                    Row(Modifier.fillMaxWidth().background(panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("‹", Modifier.clickable { currentId = null; draft = ""; status = "" }.padding(horizontal = 8.dp), fontSize = 30.sp, color = accent)
                        Box(Modifier.size(42.dp).clip(CircleShape).background(AvatarColors[selected.color % AvatarColors.size]), contentAlignment = Alignment.Center) { Text(selected.initials, color = ink, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selected.name, fontWeight = FontWeight.SemiBold, color = ink)
                            Text(when (selected.kind) { "channel" -> "Канал · публикации"; "group" -> "Группа · локальная"; "discussion" -> "Обсуждение"; else -> "Локальный чат" }, color = muted, fontSize = 12.sp)
                        }
                    }
                    if (selected.kind == "channel") {
                        Row(Modifier.fillMaxWidth().background(if (dark) Color(0xFF252C40) else Color(0xFFEEF0FF)).clickable {
                            val existing = chats.firstOrNull { it.discussionFor == selected.id }
                            val thread = existing ?: Chat("discussion_${selected.id}", "Обсуждение: ${selected.name}", "Комментарии к каналу", stamp(), "☷", "discussion", 4, selected.id).also { saveChats(chats + it) }
                            discussionChatId = thread.id
                            discussionParent = Message("channel_root_${selected.id}", selected.name, "Обсуждение публикаций канала «${selected.name}»", stamp(), false)
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Обсуждения и комментарии", color = accent, modifier = Modifier.weight(1f))
                            Text("Открыть ›", color = accent, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Демо-режим: сообщения не отправляются другим пользователям.", Modifier.fillMaxWidth().background(panel).padding(9.dp), color = muted, fontSize = 11.sp)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        val shown = messages[selected.id].orEmpty().ifEmpty { listOf(Message("seed_${selected.id}", selected.name, selected.preview, selected.time, false)) }
                        items(shown, key = { it.id }) { m ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                                Column(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(16.dp)).background(if (m.mine) accent else panel).padding(12.dp)) {
                                    if (!m.mine) Text(m.author, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                    if (m.voice.isNotBlank()) {
                                        Row(Modifier.clickable {
                                            runCatching {
                                                player?.release()
                                                player = MediaPlayer().apply { setDataSource(m.voice); setOnCompletionListener { it.release(); player = null; playingId = "" }; prepare(); start() }
                                                playingId = m.id
                                            }.onFailure { status = "Не удалось воспроизвести аудио." }
                                        }, verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (playingId == m.id) "Ⅱ" else "▶", color = if (m.mine) Color.White else accent, fontSize = 22.sp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Голосовое сообщение", color = if (m.mine) Color.White else ink)
                                        }
                                    } else Text(m.text, color = if (m.mine) Color.White else ink)
                                    Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(m.time, color = if (m.mine) Color(0xFFDDE2FF) else muted, fontSize = 10.sp)
                                        if (selected.kind == "channel" && m.mine) {
                                            Spacer(Modifier.width(10.dp))
                                            Text("Обсудить", color = if (m.mine) Color(0xFFDDE2FF) else accent, fontSize = 10.sp, modifier = Modifier.clickable {
                                                val thread = chats.firstOrNull { it.discussionFor == selected.id } ?: Chat("discussion_${selected.id}", "Обсуждение: ${selected.name}", "Комментарии к каналу", stamp(), "☷", "discussion", 4, selected.id).also { saveChats(chats + it) }
                                                discussionChatId = thread.id; discussionParent = m
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (status.isNotBlank()) Text(status, color = muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    Row(Modifier.fillMaxWidth().background(panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(draft, { draft = it.take(4000) }, Modifier.weight(1f), placeholder = { Text(if (selected.kind == "channel") "Новая публикация…" else "Сообщение") }, maxLines = 4, shape = RoundedCornerShape(22.dp))
                        if (selected.kind != "channel" && selected.kind != "discussion") {
                            Text(if (recording) "■" else "🎙", Modifier.clickable {
                                if (recording) {
                                    runCatching {
                                        recorder?.stop(); recorder?.release(); recorder = null; recording = false
                                        val m = Message(makeId(), username, "Голосовое сообщение", stamp(), true, recordPath)
                                        saveMessages(messages + (recordChat to (messages[recordChat].orEmpty() + m)))
                                        status = "Голосовое сообщение сохранено"
                                    }.onFailure { recording = false; runCatching { recorder?.release() }; recorder = null; File(recordPath).delete(); status = "Запись слишком короткая. Попробуй ещё раз." }
                                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    runCatching {
                                        val f = File(context.filesDir, "voice_${System.currentTimeMillis()}.m4a")
                                        recorder = createRecorder(context, f.absolutePath); recordPath = f.absolutePath; recordChat = selected.id; recording = true; status = "Идёт запись. Нажми ■ для отправки."
                                    }.onFailure { status = "Не удалось начать запись." }
                                } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }.padding(horizontal = 8.dp), color = if (recording) Color(0xFFE34E5D) else accent, fontSize = 22.sp)
                        }
                        Button(onClick = {
                            if (draft.isNotBlank()) {
                                val m = Message(makeId(), username, draft.trim(), stamp(), true)
                                saveMessages(messages + (selected.id to (messages[selected.id].orEmpty() + m)))
                                saveChats(chats.map { if (it.id == selected.id) it.copy(preview = m.text, time = m.time) else it })
                                draft = ""
                            }
                        }, enabled = draft.isNotBlank(), modifier = Modifier.padding(start = 5.dp), shape = CircleShape, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) { Text("➤") }
                    }
                    if (recording) Text("Запись идёт. Нажми ■, чтобы завершить.", Modifier.fillMaxWidth().background(panel).padding(8.dp), color = Color(0xFFE34E5D), fontSize = 12.sp)
                }
            } else {
                Scaffold(containerColor = bg, bottomBar = {
                    NavigationBar(containerColor = panel) {
                        NavigationBarItem(tab == "chats", { tab = "chats" }, icon = { Text("▤") }, label = { Text("Чаты") })
                        NavigationBarItem(tab == "contacts", { tab = "contacts" }, icon = { Text("♙") }, label = { Text("Контакты") })
                        NavigationBarItem(tab == "profile", { tab = "profile" }, icon = { Text("◉") }, label = { Text("Профиль") })
                    }
                }, floatingActionButton = { if (tab == "chats") FloatingActionButton(onClick = { showCreate = true }, containerColor = accent, contentColor = Color.White) { Text("＋", fontSize = 26.sp) } }) { padding ->
                    when (tab) {
                        "profile" -> Column(Modifier.fillMaxSize().padding(padding).padding(22.dp)) {
                            Text("Профиль и настройки", color = ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(24.dp))
                            Text(username, color = ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                            Text("Оформление", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 28.dp))
                            Text("Тема приложения", color = ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(theme == "light", { theme = "light"; prefs.edit().putString("theme", theme).apply() }, label = { Text("Светлая") })
                                FilterChip(theme == "dark", { theme = "dark"; prefs.edit().putString("theme", theme).apply() }, label = { Text("Тёмная") })
                            }
                            Text("Настройка сохраняется после перезапуска.", color = muted, fontSize = 12.sp)
                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider(color = line)
                            Text("Beta 0.2.0 · офлайн-режим", color = muted, modifier = Modifier.padding(top = 14.dp))
                            Text("Сообщения и записи пока доступны только на этом устройстве. Для реальной переписки нужен сервер.", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(onClick = { username = ""; nameDraft = ""; prefs.edit().remove("username").apply() }, modifier = Modifier.fillMaxWidth()) { Text("Сменить профиль") }
                        }
                        "contacts" -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                            Text("Контакты", color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            LazyColumn { items(chats.filter { it.kind != "channel" && it.kind != "discussion" }, key = { it.id }) { c -> ChatRow(c, { currentId = c.id }, panel, ink, muted, line) } }
                        }
                        else -> Column(Modifier.fillMaxSize().padding(padding)) {
                            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("NDRIMEDA", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text("Сообщения", color = ink, fontSize = 29.sp, fontWeight = FontWeight.Bold) }
                                Text(if (dark) "☾" else "☀", Modifier.clickable { theme = if (dark) "light" else "dark"; prefs.edit().putString("theme", theme).apply() }.padding(12.dp), color = ink, fontSize = 22.sp)
                            }
                            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), placeholder = { Text("Поиск по чатам") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(search.isBlank(), { search = "" }, label = { Text("Все") })
                                FilterChip(search == "#group", { search = "#group" }, label = { Text("Группы") })
                                FilterChip(search == "#channel", { search = "#channel" }, label = { Text("Каналы") })
                            }
                            val filtered = chats.filter { c -> search.isBlank() || (search == "#group" && c.kind == "group") || (search == "#channel" && c.kind == "channel") || (search != "#group" && search != "#channel" && (c.name.contains(search, true) || c.preview.contains(search, true))) }
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
                                items(filtered, key = { it.id }) { c -> ChatRow(c, { currentId = c.id }, panel, ink, muted, line) }
                                if (filtered.isEmpty()) item { Text("Пока пусто. Нажми +, чтобы создать чат, группу или канал.", color = muted, modifier = Modifier.padding(24.dp)) }
                            }
                        }
                    }
                }
                if (showCreate) AlertDialog(
                    onDismissRequest = { showCreate = false },
                    title = { Text("Создать") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(newName, { newName = it.take(40) }, label = { Text("Название") }, singleLine = true)
                            listOf("person" to "Личный чат (локальный)", "group" to "Групповой чат", "channel" to "Канал").forEach { (value, label) ->
                                Row(Modifier.fillMaxWidth().clickable { newKind = value }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(newKind == value, { newKind = value }); Text(label)
                                }
                            }
                            Text("Создание локальное: приглашения и публикация в интернет пока не работают.", color = muted, fontSize = 12.sp)
                        }
                    },
                    confirmButton = { TextButton(onClick = {
                        if (newName.trim().length >= 2) {
                            val id = "local_${makeId()}"
                            val c = Chat(id, newName.trim(), if (newKind == "channel") "Новый канал" else "Новый чат", stamp(), newName.trim().take(1).uppercase(), newKind, chats.size % AvatarColors.size)
                            saveChats(chats + c); currentId = id; newName = ""; showCreate = false
                        }
                    }) { Text("Создать") } },
                    dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Отмена") } }
                )
            }
        }
    }
}

@Composable
private fun ChatRow(c: Chat, onClick: () -> Unit, panel: Color, ink: Color, muted: Color, line: Color) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(AvatarColors[c.color % AvatarColors.size]), contentAlignment = Alignment.Center) {
                Text(when (c.kind) { "channel" -> "◉"; "group" -> "♧"; "discussion" -> "☷"; else -> c.initials }, color = ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(c.preview, color = muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(c.time, color = muted, fontSize = 11.sp)
        }
        HorizontalDivider(color = line, thickness = 0.7.dp, modifier = Modifier.padding(start = 82.dp))
    }
}
