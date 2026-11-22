package com.lamia.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var speechResult: ((String) -> Unit)? = null
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecognition()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { LamiaScreen(
            onSpeak = { speak(it) },
            onListen = { callback -> listen(callback) },
            onAsk = { question, callback -> askBackend(question, callback) },
            onService = { startMicService() },
            onStopService = { stopService(Intent(this, MicForegroundService::class.java)) }
        ) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val preferred = tts?.setLanguage(Locale("bn", "BD")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (preferred == TextToSpeech.LANG_NOT_SUPPORTED || preferred == TextToSpeech.LANG_MISSING_DATA) {
                tts?.setLanguage(Locale.US)
            }
        }
    }
    private fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lamu_reply") }

    private fun listen(onText: (String) -> Unit) {
        speechResult = onText
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO); return
        }
        startRecognition()
    }
    private fun startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { speak("Speech recognition is not available on this device."); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                speechResult?.invoke(text)
            }
            override fun onError(error: Int) { }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("bn-BD", "en-US"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to LAMU")
        }
        recognizer?.startListening(intent)
    }

    private fun askBackend(prompt: String, callback: (String) -> Unit) {
        lifecycleScope.launchCompat {
            val answer = withContext(Dispatchers.IO) {
                try {
                    val conn = URL(AppConfig.BACKEND_URL).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"; conn.connectTimeout = 20000; conn.readTimeout = 30000
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    val body = JSONObject().put("message", prompt).toString()
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(response).optString("reply", "No reply")
                } catch (e: Exception) { "Backend error: ${e.message}. Check backend URL and server." }
            }
            callback(answer)
        }
    }
    private fun startMicService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO); return
        }
        ContextCompat.startForegroundService(this, Intent(this, MicForegroundService::class.java))
    }

    override fun onDestroy() { recognizer?.destroy(); tts?.shutdown(); super.onDestroy() }
}

private fun androidx.lifecycle.LifecycleOwner.lifecycleScopeCompat(block: suspend () -> Unit) {
    androidx.lifecycle.lifecycleScope.launch { block() }
}
private fun ComponentActivity.launchCompat(block: suspend () -> Unit) {
    androidx.lifecycle.lifecycleScope.launch { block() }
}

@Composable
private fun LamiaScreen(
    onSpeak: (String) -> Unit,
    onListen: (String) -> Unit,
    onAsk: (String, (String) -> Unit) -> Unit,
    onService: () -> Unit,
    onStopService: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("হ্যালো! আমি LAMU। কীভাবে সাহায্য করতে পারি?") }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB69CFF), background = Color(0xFF090B16), surface = Color(0xFF15182A))) {
        Column(Modifier.fillMaxSize().background(Color(0xFF090B16)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("LAMIA AI", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("Your voice companion • LAMU", color = Color(0xFFB9BBD0))
                }
                IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(142.dp).background(Brush.radialGradient(listOf(Color(0xFF8B5CF6), Color(0xFF2563EB), Color.Transparent)), CircleShape))
                Text("LAMU", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            }
            if (showSettings) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2035)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Privacy & voice", color = Color.White)
                        Text("Speech recognition uses your device's speech service. TTS voice depends on installed voices.", color = Color(0xFFB9BBD0))
                        Text("Wake word: not enabled in this starter build.", color = Color(0xFFFFC56D))
                        Row { Button(onClick = onService) { Text("Start mic service") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = onStopService) { Text("Stop") } }
                    }
                }
            }
            Text("Conversation", color = Color(0xFFB9BBD0))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), reverseLayout = true) {
                items(history.reversed()) { line -> Text(line, color = Color.White, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Color(0xFF171B2D), RoundedCornerShape(12.dp)).padding(12.dp)) }
                item { Text(reply, color = Color.White, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Color(0xFF171B2D), RoundedCornerShape(12.dp)).padding(12.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask LAMU…") }, singleLine = true)
                IconButton(onClick = { onListen { recognized -> input = recognized } }) { Icon(Icons.Default.Mic, "Speak", tint = Color(0xFFB69CFF)) }
                IconButton(onClick = {
                    val q = input.trim()
                    if (q.isNotEmpty() && !busy) {
                        history.add("You: $q"); input = ""; busy = true
                        onAsk(q) { ans -> reply = ans; history.add("LAMU: $ans"); busy = false; onSpeak(ans) }
                    }
                }, enabled = !busy) { Icon(Icons.Default.Send, "Send", tint = Color.White) }
            }
            Text("Trading content is educational only. No guaranteed signals or profit.", color = Color(0xFF9297B2), style = MaterialTheme.typography.labelSmall)
        }
    }
}
