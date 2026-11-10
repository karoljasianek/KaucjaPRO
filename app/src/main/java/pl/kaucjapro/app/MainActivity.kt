package pl.kaucjapro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pl.kaucjapro.app.ui.KaucjaApp
import pl.kaucjapro.app.ui.KaucjaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KaucjaTheme {
                KaucjaApp()
            }
        }
    }
}
