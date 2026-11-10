package pl.kaucjapro.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.kaucjapro.app.data.Pack
import java.net.HttpURLConnection
import java.net.URL

/**
 * Odpytuje otwartą bazę Open Food Facts o nazwę produktu i rodzaj opakowania.
 * Wysyłany jest tylko kod kreskowy. Wynik to wyłącznie podpowiedź - użytkownik ją potwierdza.
 */
object ProductLookup {

    data class Guess(val name: String?, val pack: Pack?)

    suspend fun fetch(barcode: String): Guess? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://world.openfoodfacts.org/api/v2/product/$barcode.json" +
                    "?fields=product_name,brands,quantity,packaging,packaging_tags"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "KaucjaPRO/1.0 (Android)")
            }
            try {
                if (conn.responseCode != 200) return@withContext null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                if (root.optInt("status", 0) != 1) return@withContext null
                val product = root.optJSONObject("product") ?: return@withContext null

                val name = listOf(
                    product.optString("brands").substringBefore(','),
                    product.optString("product_name"),
                    product.optString("quantity")
                ).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ").ifBlank { null }

                val tags = product.optJSONArray("packaging_tags")
                val tagText = if (tags == null) "" else (0 until tags.length()).joinToString(" ") { tags.getString(it) }
                Guess(name, guessPack(tagText + " " + product.optString("packaging")))
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun guessPack(text: String): Pack? {
        val tokens = text.lowercase().split(Regex("[^a-ząćęłńóśźż]+")).filter { it.isNotEmpty() }.toSet()
        return when {
            tokens.any { it == "glass" || it == "verre" || it.startsWith("szk") } -> Pack.GLASS
            tokens.any {
                it in setOf(
                    "pet", "plastic", "plastik", "plastikowa", "can", "cans", "tin",
                    "metal", "aluminium", "aluminum", "puszka"
                )
            } -> Pack.PET_CAN
            else -> null
        }
    }
}
