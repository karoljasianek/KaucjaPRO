package pl.kaucjapro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.kaucjapro.app.KaucjaViewModel
import pl.kaucjapro.app.data.Pack
import pl.kaucjapro.app.data.Settings
import pl.kaucjapro.app.data.centsFor
import pl.kaucjapro.app.data.formatZl
import pl.kaucjapro.app.data.parseCents
import pl.kaucjapro.app.data.toInputString
import pl.kaucjapro.app.ocr.ReceiptParser

private val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/** Stawki kaucji używane jako domyślne przy dodawaniu nowych produktów. */
@Composable
fun SettingsDialog(settings: Settings, onSave: (Settings) -> Unit, onDismiss: () -> Unit) {
    var pet by remember { mutableStateOf(settings.petCanCents.toInputString()) }
    var glass by remember { mutableStateOf(settings.glassCents.toInputString()) }
    val petCents = parseCents(pet)
    val glassCents = parseCents(glass)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stawki kaucji") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Domyślne kwoty (zł) podpowiadane przy nowych produktach. " +
                        "Sprawdź aktualne stawki w przepisach — mogą się zmienić.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pet,
                    onValueChange = { pet = it },
                    label = { Text("PET do 3 l / puszka do 1 l") },
                    singleLine = true,
                    keyboardOptions = decimalKeyboard
                )
                OutlinedTextField(
                    value = glass,
                    onValueChange = { glass = it },
                    label = { Text("Szklana butelka do 1,5 l") },
                    singleLine = true,
                    keyboardOptions = decimalKeyboard
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = petCents != null && glassCents != null,
                onClick = { onSave(Settings(petCents ?: 50, glassCents ?: 100)) }
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

/** Pytanie o typ opakowania dla produktu, którego jeszcze nie znamy. */
@Composable
fun UnknownProductDialog(
    pending: KaucjaViewModel.Pending,
    settings: Settings,
    onConfirm: (name: String, pack: Pack, cents: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val startPack = pending.suggestedPack ?: Pack.PET_CAN
    var name by remember(pending.barcode) { mutableStateOf(pending.suggestedName ?: "") }
    var pack by remember(pending.barcode) { mutableStateOf(startPack) }
    var amount by remember(pending.barcode) { mutableStateOf(settings.centsFor(startPack).toInputString()) }
    val cents = parseCents(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowy produkt") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Kod: ${pending.barcode}", style = MaterialTheme.typography.bodySmall)
                if (pending.suggestedPack != null) {
                    Text(
                        "Typ opakowania odgadnięto z bazy Open Food Facts — sprawdź, czy się zgadza.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "Nie znam tego produktu. Wybierz rodzaj opakowania — zapamiętam go na przyszłość.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa produktu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Pack.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pack = option
                                if (option != Pack.OTHER) amount = settings.centsFor(option).toInputString()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = pack == option, onClick = null)
                        Text(option.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Kaucja za sztukę (zł)") },
                    singleLine = true,
                    keyboardOptions = decimalKeyboard,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = cents != null && cents > 0,
                onClick = { onConfirm(name.trim(), pack, cents ?: 0) }
            ) { Text("Dodaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

/**
 * Sprawdzenie wyniku OCR paragonu lub ręczne dodanie kwoty.
 * [result] == null oznacza wpis ręczny (bez listy wykrytych pozycji).
 */
@Composable
fun EntryDialog(
    heading: String,
    defaultLabel: String,
    result: ReceiptParser.Result?,
    onConfirm: (label: String, cents: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(defaultLabel) }
    var amount by remember {
        mutableStateOf(if (result != null && result.totalCents > 0) result.totalCents.toInputString() else "")
    }
    var showRaw by remember { mutableStateOf(false) }
    val cents = parseCents(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (result != null) {
                    if (result.lines.isEmpty()) {
                        Text("Nie znaleziono na paragonie pozycji z kaucją. Wpisz kwotę ręcznie.")
                    } else {
                        Text("Wykryte pozycje:", style = MaterialTheme.typography.labelLarge)
                        result.lines.forEach { line ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    line.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    line.cents.formatZl(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                    TextButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "Ukryj odczytany tekst" else "Pokaż odczytany tekst")
                    }
                    if (showRaw) {
                        Text(
                            result.raw.ifBlank { "(nic nie odczytano)" },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                        )
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Opis (np. sklep)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Kaucja łącznie (zł)") },
                    singleLine = true,
                    keyboardOptions = decimalKeyboard,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = cents != null && cents > 0,
                onClick = { onConfirm(label.trim().ifBlank { defaultLabel }, cents ?: 0) }
            ) { Text("Dodaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}
