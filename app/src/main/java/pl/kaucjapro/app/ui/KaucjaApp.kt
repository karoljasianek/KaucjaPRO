package pl.kaucjapro.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.kaucjapro.app.KaucjaViewModel

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Start", Icons.Default.Home),
    BOTTLES("Butelki", Icons.Default.Add),
    RECEIPTS("Paragony", Icons.Default.ShoppingCart),
    MAP("Mapa", Icons.Default.Place),
    HISTORY("Historia", Icons.Default.DateRange)
}

@Composable
fun KaucjaApp(vm: KaucjaViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            // Tylko wybrany ekran jest komponowany, więc aparat/mapa są aktywne tylko gdy widoczne.
            when (tab) {
                Tab.HOME -> HomeScreen(vm, onNavigate = { tab = it })
                Tab.BOTTLES -> BottleScreen(vm)
                Tab.RECEIPTS -> ReceiptScreen(vm)
                Tab.MAP -> MapScreen()
                Tab.HISTORY -> HistoryScreen(vm)
            }
        }
    }
}
