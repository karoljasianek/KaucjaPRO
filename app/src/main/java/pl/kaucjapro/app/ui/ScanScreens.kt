package pl.kaucjapro.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.kaucjapro.app.KaucjaViewModel
import pl.kaucjapro.app.data.formatZl
import pl.kaucjapro.app.ocr.Ocr
import pl.kaucjapro.app.ocr.ReceiptParser
import java.io.File

/** Skaner kodów kreskowych butelek i puszek. */
@Composable
fun BottleScreen(vm: KaucjaViewModel) {
    CameraPermissionGate {
        val entries by vm.entries.collectAsStateWithLifecycle()
        val settings by vm.settings.collectAsStateWithLifecycle()
        val pending by vm.pending.collectAsStateWithLifecycle()
        val lastAdd by vm.lastAdd.collectAsStateWithLifecycle()
        val lookingUp by vm.lookingUp.collectAsStateWithLifecycle()
        val analyzer = remember { BarcodeAnalyzer { code -> vm.onBarcode(code) } }
        var torch by remember { mutableStateOf(false) }
        val total = entries.filter { !it.returned }.sumOf { it.cents }

        Box(Modifier.fillMaxSize()) {
            CameraPreview(Modifier.fillMaxSize(), analyzer = analyzer, torch = torch)

            // ramka celownika
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.75f)
                    .height(140.dp)
                    .border(BorderStroke(3.dp, Color.White.copy(alpha = 0.9f)), RoundedCornerShape(16.dp))
            )

            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    "Razem: ${total.formatZl()}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            FilterChip(
                selected = torch,
                onClick = { torch = !torch },
                label = { Text("Latarka") },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            )

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (lookingUp) LinearProgressIndicator(Modifier.fillMaxWidth())

                val last = lastAdd
                if (last != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Dodano: ${last.title}", fontWeight = FontWeight.Bold, maxLines = 2)
                                Text(
                                    "+${last.unitCents.formatZl()} • tego produktu: ${last.totalQty} szt.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { vm.undoLast() }) { Text("Cofnij") }
                        }
                    }
                } else {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.6f)) {
                        Text(
                            "Skieruj aparat na kod kreskowy butelki lub puszki",
                            color = Color.White,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        pending?.let { p ->
            UnknownProductDialog(
                pending = p,
                settings = settings,
                onConfirm = { name, pack, cents -> vm.confirmPending(name, pack, cents) },
                onDismiss = { vm.cancelPending() }
            )
        }
    }
}

/** Skaner paragonów (OCR). Aparat u góry, przyciski na dole. */
@Composable
fun ReceiptScreen(vm: KaucjaViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var review by remember { mutableStateOf<ReceiptParser.Result?>(null) }
    var manual by remember { mutableStateOf(false) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }

    fun process(uri: Uri, cleanup: File? = null) {
        scope.launch {
            busy = true
            error = null
            try {
                review = ReceiptParser.parse(Ocr.readText(context, uri))
            } catch (e: Exception) {
                error = "Nie udało się odczytać paragonu: ${e.message ?: "nieznany błąd"}"
            } finally {
                busy = false
                cleanup?.delete()
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) process(uri)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CameraPermissionGate {
                CameraPreview(Modifier.fillMaxSize(), imageCapture = imageCapture)
            }
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Połóż paragon płasko, w dobrym świetle, tak by pozycje z kaucją były widoczne. " +
                        "Rozpoznawanie działa na telefonie, a wynik sprawdzisz przed zapisem.",
                    style = MaterialTheme.typography.bodySmall
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val file = File(context.cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
                        busy = true
                        error = null
                        try {
                            imageCapture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        process(Uri.fromFile(file), file)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        busy = false
                                        error = "Błąd aparatu: ${exception.message}"
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            busy = false
                            error = "Aparat nie jest gotowy. Sprawdź zgodę na aparat."
                        }
                    }
                ) { Text("Zrób zdjęcie paragonu") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Z galerii") }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { manual = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Wpisz ręcznie") }
                }
            }
        }
    }

    review?.let { result ->
        EntryDialog(
            heading = "Sprawdź kaucję z paragonu",
            defaultLabel = "Paragon",
            result = result,
            onConfirm = { label, cents ->
                vm.addReceipt(label, cents)
                review = null
            },
            onDismiss = { review = null }
        )
    }

    if (manual) {
        EntryDialog(
            heading = "Dodaj kaucję ręcznie",
            defaultLabel = "Wpis ręczny",
            result = null,
            onConfirm = { label, cents ->
                vm.addManual(label, cents)
                manual = false
            },
            onDismiss = { manual = false }
        )
    }
}
