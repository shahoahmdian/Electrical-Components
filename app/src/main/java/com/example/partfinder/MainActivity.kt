package com.example.partfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) { App() }
                }
            }
        }
    }
}

@Composable
fun App() {
    var tab by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Part?>(null) }
    val part = selected
    if (part != null) {
        BackHandler { selected = null }
        Detail(part, onBack = { selected = null }, onResistor = { selected = null; tab = 1 })
        return
    }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("بایگانی") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("رنگ مقاومت") })
        }
        if (tab == 0) Archive { selected = it } else ResistorScreen()
    }
}

@Composable
fun Archive(onOpen: (Part) -> Unit) {
    val ctx = LocalContext.current
    val all = remember { Db.load(ctx) }
    val prefs = remember { ctx.getSharedPreferences("p", 0) }
    val scope = rememberCoroutineScope()
    val photoFile = remember { File(ctx.cacheDir, "shot.jpg") }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var onlineMode by remember { mutableStateOf(false) }
    var onlineResults by remember { mutableStateOf<List<Part>>(emptyList()) }
    var anthKey by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var dkId by remember { mutableStateOf(prefs.getString("dk_id", "") ?: "") }
    var dkSecret by remember { mutableStateOf(prefs.getString("dk_secret", "") ?: "") }

    fun runOnline(q: String) {
        if (dkId.isBlank() || dkSecret.isBlank()) { showKey = true; return }
        busy = true
        status = "جستجو در DigiKey..."
        scope.launch {
            try {
                onlineResults = withContext(Dispatchers.IO) { DigiKey.search(dkId, dkSecret, q) }
                status = "${onlineResults.size} نتیجه از DigiKey"
            } catch (e: Exception) {
                status = "خطا: ${e.message}"
            }
            busy = false
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            busy = true
            status = "در حال شناسایی قطعه..."
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) { Vision.identify(anthKey, Vision.prepare(photoFile)) }
                    val j = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
                    query = listOf("part_number", "type", "markings").joinToString(" ") { j.optString(it) }
                    status = "شناسایی: ${j.optString("part_number")} ${j.optString("type")}"
                    busy = false
                    if (onlineMode) runOnline(query)
                } catch (e: Exception) {
                    status = "خطا در شناسایی: ${e.message}"
                    busy = false
                }
            }
        }
    }

    fun takePhoto() {
        if (anthKey.isBlank()) { showKey = true; return }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", photoFile)
        launcher.launch(uri)
    }

    val local = remember(query) { Db.search(all, query) }
    val results = if (onlineMode) onlineResults else local

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("بایگانی قطعات الکترونیکی", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (onlineMode) "حالت آنلاین (DigiKey)" else "حالت آفلاین", Modifier.weight(1f))
            Switch(checked = onlineMode, onCheckedChange = { onlineMode = it; status = "" })
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("نام، مدل یا نوع (مثلاً BC547، دیود، op-amp)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (onlineMode) runOnline(query) })
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onlineMode) Button(onClick = { runOnline(query) }, enabled = !busy) { Text("جستجو") }
            Button(onClick = { takePhoto() }, enabled = !busy) { Text("عکس") }
            OutlinedButton(onClick = { showKey = true }) { Text("کلیدها") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
        Text("${results.size} نتیجه", style = MaterialTheme.typography.labelMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { p ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(p) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PartArt(p, Modifier.size(72.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(p.category, style = MaterialTheme.typography.labelMedium)
                            Text(p.description, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showKey) {
        AlertDialog(
            onDismissRequest = { showKey = false },
            title = { Text("کلیدهای API") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(anthKey, { anthKey = it }, singleLine = true, label = { Text("Anthropic key (عکس)") })
                    OutlinedTextField(dkId, { dkId = it }, singleLine = true, label = { Text("DigiKey Client ID") })
                    OutlinedTextField(dkSecret, { dkSecret = it }, singleLine = true, label = { Text("DigiKey Client Secret") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putString("key", anthKey.trim()).putString("dk_id", dkId.trim())
                        .putString("dk_secret", dkSecret.trim()).apply()
                    showKey = false
                }) { Text("ذخیره") }
            }
        )
    }
}

@Composable
fun Detail(p: Part, onBack: () -> Unit, onResistor: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← بازگشت") }
        PartArt(p, Modifier.fillMaxWidth().height(170.dp))
        Text(p.name, style = MaterialTheme.typography.headlineSmall)
        Text(p.category, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(12.dp))
        if (kindOf(p) == Kind.RES && p.imageUrl.isEmpty())
            Button(onClick = onResistor) { Text("خواندن مقدار از روی رنگ‌ها") }
        if (p.description.isNotEmpty()) Section("توضیح", p.description)
        Text("مشخصات", style = MaterialTheme.typography.titleMedium)
        p.specs.forEach { Text("• $it") }
        Spacer(Modifier.height(12.dp))
        if (p.application.isNotEmpty()) Section("کاربرد در مدار", p.application)
        if (p.pinout.isNotEmpty()) Section("پایه‌ها", p.pinout)
        if (p.datasheet.startsWith("http")) TextButton(onClick = { uri.openUri(p.datasheet) }) { Text("دیتاشیت") }
        if (p.link.startsWith("http")) TextButton(onClick = { uri.openUri(p.link) }) { Text("صفحه DigiKey") }
    }
}

@Composable
fun Section(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(body)
    Spacer(Modifier.height(12.dp))
}
