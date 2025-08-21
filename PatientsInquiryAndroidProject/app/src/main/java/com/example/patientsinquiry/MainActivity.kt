package com.example.patientsinquiry

import android.Manifest
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.patientsinquiry.data.AppDatabase
import com.example.patientsinquiry.data.QA
import com.example.patientsinquiry.data.Reminder
import com.example.patientsinquiry.nlu.PromptHelper
import com.example.patientsinquiry.nlu.parseIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var prefs: SharedPreferences
    private var exo: ExoPlayer? = null

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, "Microphone permission needed.", Toast.LENGTH_SHORT).show()
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString("media_folder", it.toString()).apply()
            Toast.makeText(this, "Media folder set.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        prefs = getSharedPreferences("pi_prefs", Context.MODE_PRIVATE)

        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            PatientsInquiryApp(
                onMic = { startVoiceInput() },
                onPickFolder = { pickFolder.launch(null) },
                onPlayCelebrate = { playRandomFromFolder() },
                onOpenLink = { openLink(it) }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
        exo?.release()
    }

    override fun onInit(status: Int) {
        // TTS ready
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            speak("Patients Inquiry ready. Tap the microphone and say 'Hey Inquiry' followed by your request.")
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "PI_TTS")
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: 'Hey Inquiry, ...'")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No speech recognizer.", Toast.LENGTH_SHORT).show()
        }
    }

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val spoken = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: return@registerForActivityResult
        handleUtterance(spoken)
    }

    private fun handleUtterance(spoken: String) {
        val normalized = spoken.trim()
        val activated = normalized.startsWith("hey inquiry") || normalized.startsWith("hey inquery") || normalized.startsWith("hey enquiry")
        if (!activated) {
            speak("Please start with 'Hey Inquiry' to activate.")
            return
        }
        val command = normalized.removePrefix("hey inquiry").removePrefix("hey inquery").removePrefix("hey enquiry").trim()
        val intent = parseIntent(command)
        when (intent.type) {
            "prompt_help" -> {
                speak(PromptHelper.randomTip())
            }
            "search" -> {
                val q = intent.args["query"] ?: command
                lifecycleScope.launch(Dispatchers.IO) {
                    val dao = AppDatabase.get(this@MainActivity).qaDao()
                    val results = dao.search("%${q.lowercase()}%")
                    launch(Dispatchers.Main) {
                        if (results.isEmpty()) {
                            speak("I didn't find anything. Here are some trusted care links in the app.")
                        } else {
                            speak("I found ${results.size} entri" + if (results.size==1) "y." else "es.")
                        }
                    }
                }
            }
            "set_reminder" -> {
                val whenText = intent.args["raw"] ?: command
                val whenMillis = com.example.patientsinquiry.time.SimpleTimeParser.parseWhen(whenText)
                if (whenMillis == null) {
                    speak("I couldn't parse the time. Try 'tomorrow at 8 pm' or 'in 2 hours'.")
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val dao = AppDatabase.get(this@MainActivity).reminderDao()
                        val id = dao.insert(Reminder(dueTs = whenMillis, text = command))
                        com.example.patientsinquiry.reminders.ReminderScheduler.schedule(this@MainActivity, id.toInt(), whenMillis, command)
                        launch(Dispatchers.Main) { speak("Reminder set.") }
                    }
                }
            }
            "list_reminders" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val dao = AppDatabase.get(this@MainActivity).reminderDao()
                    val list = dao.list(false)
                    launch(Dispatchers.Main) {
                        if (list.isEmpty()) speak("No pending reminders.")
                        else speak("You have ${list.size} pending reminders.")
                    }
                }
            }
            "done_reminder" -> {
                val idStr = intent.args["id"]
                val id = idStr?.toIntOrNull()
                if (id==null) speak("Please mention the reminder number.")
                else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val dao = AppDatabase.get(this@MainActivity).reminderDao()
                        dao.markDone(id)
                        launch(Dispatchers.Main) { speak("Reminder marked done.") }
                    }
                }
            }
            "celebrate" -> {
                val ok = playRandomFromFolder()
                if (!ok) speak("Set your media folder first in Settings.")
                else speak("Celebrating!")
            }
            "set_media_folder" -> {
                // handled via UI pick, but we accept voice hint
                speak("Opening folder picker.")
                pickFolder.launch(null)
            }
            "symptom_report" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val dao = AppDatabase.get(this@MainActivity).qaDao()
                    val now = Instant.now().toString()
                    dao.insert(QA(ts = now, topic = "symptom", question = spoken, answer = SuggestionEngine.answerFor(spoken)))
                    launch(Dispatchers.Main) { speak("Saved. I added some guidance and care links in the app.") }
                }
            }
            "help" -> {
                speak("You can say: I feel tired starting yesterday, severity 6. Or say: Remind me tomorrow at 8 pm to check symptoms. Or: Celebrate with my music.")
            }
            else -> {
                speak("I can give options or open care links if I don't know the answer. Check the Care tab.")
            }
        }
    }

    private fun playRandomFromFolder(): Boolean {
        val uriStr = prefs.getString("media_folder", null) ?: return false
        val treeUri = Uri.parse(uriStr)
        val doc = DocumentFile.fromTreeUri(this, treeUri) ?: return false
        val media = doc.listFiles().filter {
            val name = it.name?.lowercase() ?: ""
            name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".mp4") || name.endsWith(".mkv")
        }
        if (media.isEmpty()) return false
        val pick = media.random().uri
        exo?.release()
        exo = ExoPlayer.Builder(this).build().also { p ->
            p.setMediaItem(MediaItem.fromUri(pick))
            p.prepare()
            p.play()
        }
        return true
    }
}

@Composable
fun PatientsInquiryApp(
    onMic: () -> Unit,
    onPickFolder: () -> Unit,
    onPlayCelebrate: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    val tabs = listOf("Ask", "History", "Reminders", "Care", "Settings")
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Patients Inquiry") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onMic) { Text("🎙") }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab==i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> AskTab(onMic)
                1 -> HistoryTab()
                2 -> RemindersTab()
                3 -> CareTab(onOpenLink)
                4 -> SettingsTab(onPickFolder, onPlayCelebrate)
            }
        }
    }
}

@Composable
fun AskTab(onMic: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Say 'Hey Inquiry' then your request.", fontWeight = FontWeight.Bold)
        Text("Not sure? Try: ${PromptHelper.randomTip()}")
        Button(onClick = onMic) { Text("Tap to Speak") }
    }
}

@Composable
fun HistoryTab() {
    val ctx = LocalContext.current
    val dao = remember { AppDatabase.get(ctx).qaDao() }
    var items by remember { mutableStateOf(listOf<QA>()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        items = if (query.isBlank()) dao.latest() else dao.search("%${query.lowercase()}%")
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(items) { qa ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(qa.ts, style = MaterialTheme.typography.labelSmall)
                        Text(qa.question, fontWeight = FontWeight.Bold)
                        if (qa.topic!=null) Text("Topic: ${qa.topic}")
                        Text(qa.answer)
                    }
                }
            }
        }
    }
}

@Composable
fun RemindersTab() {
    val ctx = LocalContext.current
    val dao = remember { AppDatabase.get(ctx).reminderDao() }
    var list by remember { mutableStateOf(listOf<Reminder>()) }
    LaunchedEffect(Unit) { list = dao.list(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn {
            items(list) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Due: " + LocalDateTime.ofInstant(Instant.ofEpochMilli(r.dueTs), ZoneId.systemDefault()).toString())
                            Text(r.text)
                        }
                        Button(onClick = {
                            dao.markDone(r.id)
                            list = dao.list(false)
                        }) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
fun CareTab(onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val json = ctx.assets.open("care_links.json").bufferedReader().use { it.readText() }
    data class Link(val label: String, val url: String)
    data class Links(val emergency: List<Link>, val trusted_health: List<Link>, val find_care: List<Link>)
    val links = com.google.gson.Gson().fromJson(json, Links::class.java)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("If answers aren't available, try these:", fontWeight = FontWeight.Bold)
        Text("Emergency")
        links.emergency.forEach { l -> Button(onClick = { onOpen(l.url) }) { Text(l.label) } }
        Text("Trusted Health")
        links.trusted_health.forEach { l -> Button(onClick = { onOpen(l.url) }) { Text(l.label) } }
        Text("Find Care")
        links.find_care.forEach { l -> Button(onClick = { onOpen(l.url) }) { Text(l.label) } }
    }
}

@Composable
fun SettingsTab(onPickFolder: () -> Unit, onPlay: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Media", fontWeight = FontWeight.Bold)
        Button(onClick = onPickFolder) { Text("Set Media Folder") }
        Button(onClick = onPlay) { Text("Test Celebrate") }
        Text("Notifications must be allowed for reminders.", style = MaterialTheme.typography.bodySmall)
    }
}

fun openLink(url: String, ctx: Context = androidx.compose.ui.platform.LocalContext.current) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Cannot open link.", Toast.LENGTH_SHORT).show()
    }
}
