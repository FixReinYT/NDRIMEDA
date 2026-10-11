package com.ndrimeda

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

private val NdrimedaBluetoothUuid: UUID = UUID.fromString("5d7b6a90-9e8f-4c5d-a1b2-7c8d9e0f1234")
private data class OfflineBluetoothMessage(val text: String, val mine: Boolean, val time: String)

@Composable
internal fun OfflineBluetoothPanel(
    dark: Boolean,
    accent: Color,
    ink: Color,
    muted: Color,
    panel: Color
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val adapter = remember { (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    val prefs = remember { context.getSharedPreferences("ndrimeda_offline_bt", Context.MODE_PRIVATE) }
    var paired by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connectedName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Связь ещё не запущена") }
    var draft by remember { mutableStateOf("") }
    var socket by remember { mutableStateOf<BluetoothSocket?>(null) }
    var serverSocket by remember { mutableStateOf<BluetoothServerSocket?>(null) }
    var listening by remember { mutableStateOf(false) }
    val lines = remember {
        mutableStateListOf<OfflineBluetoothMessage>().apply {
            runCatching {
                val arr = JSONArray(prefs.getString("history", "[]"))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(OfflineBluetoothMessage(o.optString("text"), o.optBoolean("mine"), o.optString("time")))
                }
            }
        }
    }
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) else emptyArray()
    }
    fun hasBluetoothPermissions(): Boolean =
        requiredPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun saveHistory() {
        val arr = JSONArray()
        lines.takeLast(250).forEach { arr.put(JSONObject().put("text", it.text).put("mine", it.mine).put("time", it.time)) }
        prefs.edit().putString("history", arr.toString()).apply()
    }
    fun addLine(text: String, mine: Boolean) {
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        lines.add(OfflineBluetoothMessage(text, mine, time))
        if (lines.size > 250) lines.removeAt(0)
        saveHistory()
    }
    fun beginReader(active: BluetoothSocket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(active.inputStream, Charsets.UTF_8))
                while (true) {
                    val encoded = reader.readLine() ?: break
                    val decoded = runCatching { String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8) }.getOrNull() ?: continue
                    activity?.runOnUiThread { addLine(decoded, false) }
                }
                activity?.runOnUiThread {
                    if (socket == active) {
                        socket = null
                        connectedName = ""
                        status = "Соединение закрыто"
                    }
                }
            } catch (_: Exception) {
                activity?.runOnUiThread {
                    if (socket == active) {
                        socket = null
                        connectedName = ""
                        status = "Соединение прервано"
                    }
                }
            }
        }.start()
    }
    fun refreshPaired() {
        if (adapter == null) {
            status = "На этом устройстве нет Bluetooth"
        } else if (!hasBluetoothPermissions()) {
            status = "Разреши доступ к Bluetooth"
        } else {
            paired = runCatching { adapter.bondedDevices?.toList()?.sortedBy { it.name ?: it.address } ?: emptyList() }.getOrDefault(emptyList())
            status = if (paired.isEmpty()) "Нет сопряжённых устройств. Сначала свяжи телефоны в настройках Bluetooth." else "Найдено сопряжённых устройств: ${paired.size}"
        }
    }
    fun startListening() {
        if (adapter == null) {
            status = "На этом устройстве нет Bluetooth"
            return
        }
        if (!hasBluetoothPermissions()) {
            status = "Нужно разрешение Bluetooth"
            return
        }
        if (listening) {
            status = "Ожидание входящего соединения уже включено"
            return
        }
        Thread {
            try {
                val server = adapter.listenUsingRfcommWithServiceRecord("NDRIMEDA Offline", NdrimedaBluetoothUuid)
                activity?.runOnUiThread {
                    serverSocket = server
                    listening = true
                    status = "Офлайн-связь включена. Ожидаю другой телефон…"
                }
                while (true) {
                    val incoming = server.accept()
                    activity?.runOnUiThread {
                        runCatching { socket?.close() }
                        socket = incoming
                        connectedName = runCatching { incoming.remoteDevice.name }.getOrNull() ?: "Устройство"
                        status = "Подключено: $connectedName"
                        beginReader(incoming)
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    listening = false
                    status = "Не удалось запустить приём. Проверь Bluetooth и разрешения."
                }
            }
        }.start()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPaired()
        status = if (hasBluetoothPermissions()) "Разрешения выданы. Можно включить офлайн-связь." else "Для Bluetooth нужны разрешения системы."
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { serverSocket?.close() }
            runCatching { socket?.close() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(if (dark) Color(0xFF11141B) else Color(0xFFF6F7FB)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(accent), contentAlignment = Alignment.Center) {
                Text("N", color = Color.White, fontWeight = FontWeight.Black, fontSize = 25.sp)
            }
            Column(Modifier.weight(1f)) {
                Text("Офлайн-связь", color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Bluetooth · интернет не нужен", color = muted, fontSize = 13.sp)
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (socket != null) Color(0xFF2EAF75) else Color(0xFFE2A23A)))
        }
        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(if (connectedName.isNotBlank()) "Подключено к $connectedName" else if (listening) "Ожидание подключения" else "Готово к настройке", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(status, color = muted, fontSize = 13.sp, lineHeight = 18.sp)
                Button(
                    onClick = {
                        if (adapter == null) status = "Bluetooth не поддерживается"
                        else if (!hasBluetoothPermissions()) permissionLauncher.launch(requiredPermissions)
                        else startListening()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) { Text(if (listening) "Приём включён" else "Включить приём офлайн-сообщений") }
                OutlinedButton(
                    onClick = {
                        if (adapter == null) status = "Bluetooth не поддерживается"
                        else if (!hasBluetoothPermissions()) permissionLauncher.launch(requiredPermissions)
                        else {
                            refreshPaired()
                            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("Сопрячь телефоны в настройках") }
                OutlinedButton(
                    onClick = {
                        if (adapter == null) status = "Bluetooth не поддерживается"
                        else if (!hasBluetoothPermissions()) permissionLauncher.launch(requiredPermissions)
                        else {
                            runCatching {
                                context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300))
                                status = "Разреши обнаружение телефона, затем подключайся со второго устройства."
                            }.onFailure { status = "Не удалось включить обнаружение." }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) { Text("Сделать телефон видимым на 5 минут") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Сопряжённые устройства", color = ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (!hasBluetoothPermissions()) permissionLauncher.launch(requiredPermissions) else refreshPaired() }) { Text("Обновить") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
            if (paired.isEmpty()) {
                Text("Здесь появятся телефоны, уже сопряжённые через системные настройки Bluetooth.", color = muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            } else {
                Column {
                    paired.forEach { device ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(if (dark) Color(0xFF2A3040) else Color(0xFFE9EDFF)), contentAlignment = Alignment.Center) {
                                Text("BT", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(runCatching { device.name }.getOrNull() ?: "Устройство Bluetooth", color = ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(runCatching { device.address }.getOrDefault(""), color = muted, fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                if (!hasBluetoothPermissions()) permissionLauncher.launch(requiredPermissions)
                                else Thread {
                                    try {
                                        adapter?.cancelDiscovery()
                                        val outgoing = device.createRfcommSocketToServiceRecord(NdrimedaBluetoothUuid)
                                        outgoing.connect()
                                        activity?.runOnUiThread {
                                            runCatching { socket?.close() }
                                            socket = outgoing
                                            connectedName = runCatching { device.name }.getOrNull() ?: "Устройство"
                                            status = "Подключено: $connectedName"
                                            beginReader(outgoing)
                                        }
                                    } catch (_: Exception) {
                                        activity?.runOnUiThread { status = "Не удалось подключиться. На втором телефоне включи приём офлайн-сообщений." }
                                    }
                                }.start()
                            }) { Text("Подключить") }
                        }
                        HorizontalDivider(color = if (dark) Color(0xFF303746) else Color(0xFFE9EBF2))
                    }
                }
            }
        }
        Text("Сообщения", color = ink, fontWeight = FontWeight.Bold)
        Card(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(min = 110.dp, max = 260.dp), colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
            if (lines.isEmpty()) {
                Text("Пока нет сообщений. Включи приём на обоих телефонах, затем подключи один к другому.", color = muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(lines) { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.mine) Arrangement.End else Arrangement.Start) {
                            Column(Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(13.dp)).background(if (line.mine) accent else if (dark) Color(0xFF252C40) else Color(0xFFF0F2F7)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(line.text, color = if (line.mine) Color.White else ink, fontSize = 14.sp)
                                Text(line.time, color = if (line.mine) Color(0xFFDDE2FF) else muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft, { draft = it.take(2000) }, Modifier.weight(1f), placeholder = { Text("Сообщение без интернета") }, maxLines = 3, shape = RoundedCornerShape(18.dp))
            Button(onClick = {
                val active = socket
                val message = draft.trim()
                if (message.isNotBlank() && active != null && active.isConnected) {
                    draft = ""
                    addLine(message, true)
                    Thread {
                        runCatching {
                            val encoded = Base64.encodeToString(message.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                            OutputStreamWriter(active.outputStream, Charsets.UTF_8).apply { write(encoded + "\n"); flush() }
                        }.onFailure { activity?.runOnUiThread { status = "Не удалось отправить сообщение." } }
                    }.start()
                } else if (active == null) status = "Сначала подключи второй телефон по Bluetooth."
            }, enabled = draft.isNotBlank() && socket?.isConnected == true, shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) { Text("➤") }
        }
        Text("Это локальная Bluetooth-связь на близком расстоянии. Переписка по Bluetooth пока не использует отдельное сквозное шифрование NDRIMEDA.", color = muted, fontSize = 11.sp, lineHeight = 15.sp)
    }
}
