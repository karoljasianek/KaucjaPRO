package pl.kaucjapro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.kaucjapro.app.KaucjaViewModel
import pl.kaucjapro.app.data.Kind
import pl.kaucjapro.app.data.formatZl

@Composable
fun HomeScreen(vm: KaucjaViewModel, onNavigate: (Tab) -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }

    val active = entries.filter { !it.returned }
    val total = active.sumOf { it.cents }
    val bottles = active.filter { it.kind == Kind.BOTTLE }
    val bottleCents = bottles.sumOf { it.cents }
    val bottleCount = bottles.sumOf { it.qty }
    val otherCents = active.filter { it.kind != Kind.BOTTLE }.sumOf { it.cents }
    val returnedCents = entries.filter { it.returned }.sumOf { it.cents }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "KaucjaPRO",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Twoja kaucja do odzyskania", style = MaterialTheme.typography.titleMedium)
                Text(
                    total.formatZl(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Opakowań w posiadaniu: $bottleCount szt.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryRow("Zeskanowane butelki i puszki", bottleCents.formatZl())
                SummaryRow("Paragony i wpisy ręczne", otherCents.formatZl())
                SummaryRow("Już odzyskane", returnedCents.formatZl())
            }
        }

        Button(onClick = { onNavigate(Tab.BOTTLES) }, modifier = Modifier.fillMaxWidth()) {
            Text("Skanuj butelkę lub puszkę")
        }
        Button(onClick = { onNavigate(Tab.RECEIPTS) }, modifier = Modifier.fillMaxWidth()) {
            Text("Skanuj paragon")
        }
        OutlinedButton(onClick = { onNavigate(Tab.MAP) }, modifier = Modifier.fillMaxWidth()) {
            Text("Znajdź najbliższy kaucjomat")
        }
        if (total > 0) {
            OutlinedButton(onClick = { confirmAll = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Oddałem wszystko — oznacz jako zwrócone")
            }
        }

        Text(
            "Uwaga: jeśli kaucja z paragonu dotyczy butelek, które też skanujesz, " +
                "dodaj ją tylko raz — aplikacja nie rozpoznaje powtórzeń. " +
                "Wszystkie dane są zapisane tylko na tym telefonie.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onSave = {
                vm.saveSettings(it)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Oznaczyć wszystko jako zwrócone?") },
            text = { Text("Kwota ${total.formatZl()} zostanie przeniesiona do „Już odzyskane”. Możesz to cofnąć w Historii.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.markAllReturned()
                    confirmAll = false
                }) { Text("Tak, oddałem") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Anuluj") } }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
