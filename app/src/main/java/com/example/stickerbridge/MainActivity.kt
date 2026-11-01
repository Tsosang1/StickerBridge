package com.example.stickerbridge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.stickerbridge.processor.ExportHelper
import com.example.stickerbridge.processor.StickerProcessor
import com.example.stickerbridge.whatsapp.StickerContentProvider
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var processor: StickerProcessor
    private var sharedMediaUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processor = StickerProcessor(applicationContext)
        processor.ensureTrayIconsExist()

        handleIncomingIntent(intent)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StickerBridgeScreen(
                        processor = processor,
                        incomingUri = sharedMediaUri,
                        onClearIncoming = { sharedMediaUri = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) sharedMediaUri = uri
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerBridgeScreen(
    processor: StickerProcessor,
    incomingUri: Uri?,
    onClearIncoming: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var autoRemoveBg by remember { mutableStateOf(false) }

    val staticDir = remember { File(context.filesDir, "stickers/static") }
    val animDir = remember { File(context.filesDir, "stickers/animated") }

    var staticStickers by remember { mutableStateOf(staticDir.listFiles { f -> f.extension == "webp" }?.toList() ?: emptyList()) }
    var animStickers by remember { mutableStateOf(animDir.listFiles { f -> f.extension == "webp" }?.toList() ?: emptyList()) }

    var selectedStickerForExport by remember { mutableStateOf<File?>(null) }

    val refreshStickers = {
        staticStickers = staticDir.listFiles { f -> f.extension == "webp" }?.toList() ?: emptyList()
        animStickers = animDir.listFiles { f -> f.extension == "webp" }?.toList() ?: emptyList()
    }

    val whatsappLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Added to WhatsApp!", Toast.LENGTH_SHORT).show()
        }
    }

    fun addToWhatsApp(packId: String, packName: String) {
        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", packId)
            putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
            putExtra("sticker_pack_name", packName)
        }
        try {
            whatsappLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "WhatsApp error", Toast.LENGTH_LONG).show()
        }
    }

    fun processUri(uri: Uri) {
        scope.launch {
            isProcessing = true
            try {
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val timestamp = System.currentTimeMillis()

                if (mimeType.contains("gif") || mimeType.startsWith("video/")) {
                    val result = processor.processGifUriToAnimatedPack(uri, "anim_$timestamp")
                    if (result != null) {
                        refreshStickers()
                        Toast.makeText(context, "Animated Sticker Saved!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to parse animation", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val bitmap = processor.decodeBitmapFromUri(uri)
                    if (bitmap != null) {
                        processor.processAndSave(bitmap, "static_$timestamp", autoRemoveBg)
                        refreshStickers()
                        Toast.makeText(context, "Static Sticker Saved!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error processing", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
                onClearIncoming()
            }
        }
    }

    LaunchedEffect(incomingUri) {
        incomingUri?.let { processUri(it) }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processUri(it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("StickerBridge", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { mediaPickerLauncher.launch("*/*") }, enabled = !isProcessing) {
                Text(if (isProcessing) "Importing..." else "Import Media")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoRemoveBg, onCheckedChange = { autoRemoveBg = it })
            Text("Auto-remove background on photos")
        }

        if (isProcessing) CircularProgressIndicator(modifier = Modifier.padding(12.dp))

        Spacer(modifier = Modifier.height(16.dp))

        // Static Pack UI
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Static Pack (${staticStickers.size}/30)", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { addToWhatsApp("bridge_static", "StickerBridge Static") }, enabled = staticStickers.size >= 3) {
                        Text("Add to WA")
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(staticStickers) { file ->
                        Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).clickable { selectedStickerForExport = file }) {
                            AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Animated Pack UI
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Animated Pack (${animStickers.size}/30)", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { addToWhatsApp("bridge_animated", "StickerBridge Animated") }, enabled = animStickers.size >= 3) {
                        Text("Add to WA")
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(animStickers) { file ->
                        Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).clickable { selectedStickerForExport = file }) {
                            AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for Export/Delete logic
    selectedStickerForExport?.let { file ->
        ModalBottomSheet(onDismissRequest = { selectedStickerForExport = null }) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sticker Actions", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { ExportHelper.saveToGallery(context, file); selectedStickerForExport = null }, modifier = Modifier.fillMaxWidth()) { Text("Save to Gallery") }
                Button(
                    onClick = { file.delete(); refreshStickers(); selectedStickerForExport = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete Sticker", color = MaterialTheme.colorScheme.onError) }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
