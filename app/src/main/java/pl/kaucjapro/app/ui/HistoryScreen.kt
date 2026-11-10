package pl.kaucjapro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.kaucjapro.app.KaucjaViewModel
import pl.kaucjapro.app.data.Entry
import pl.kaucjapro.app.data.formatZl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PL: Locale = Locale.forLanguageTag("pl-PL")

@Composable
fun HistoryScreen(vm: KaucjaViewModel) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Entry?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Historia",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { showAdd = true }) { Text("Dodaj ręcznie") }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Brak wpisów. Zeskanuj pierwszą butelkę lub paragon!")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onToggle = { vm.toggleReturned(entry) },
                        onDelete = { deleteTarget = entry }
                    )
                }
            }
        }
    }

    if (showAdd) {
        EntryDialog(
            heading = "Dodaj kaucję ręcznie",
            defaultLabel = "Wpis ręczny",
            result = null,
            onConfirm = { label, cents ->
                vm.addManual(label, cents)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Usunąć wpis?") },
            text = { Text("„${entry.title}” (${entry.cents.formatZl()}) zostanie usunięty z historii.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(entry.id)
                    deleteTarget = null
                }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Anuluj") } }
        )
    }
}

@Composable
private fun EntryCard(entry: Entry, onToggle: () -> Unit, onDelete: () -> Unit) {
    val date = SimpleDateFormat("d MMM yyyy, HH:mm", PL).format(Date(entry.createdAt))
    val quantity = if (entry.qty > 1) "${entry.qty} × ${entry.unitCents.formatZl()} · " else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.returned) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    textDecoration = if (entry.returned) TextDecoration.LineThrough else null
                )
                Text(
                    "${entry.kind.label} · $quantity$date" + if (entry.returned) " · zwrócone" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(entry.cents.formatZl(), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onToggle) {
                        Text(if (entry.returned) "Cofnij zwrot" else "Zwrócone")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Usuń wpis")
                    }
                }
            }
        }
    }
}
