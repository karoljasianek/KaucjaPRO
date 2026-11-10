package pl.kaucjapro.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos

data class MapPlace(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val isStore: Boolean,
    val details: String
)

/**
 * Szuka kaucjomatów w OpenStreetMap przez Overpass API.
 * Kaucjomat w OSM to: amenity=vending_machine + vending=bottle_return
 * (lub amenity=recycling + recycling_type=reverse_vending_machine).
 * Do Overpass wysyłane jest tylko zapytanie z prostokątem wokół wybranego punktu.
 */
object OverpassClient {

    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    suspend fun find(lat: Double, lon: Double, includeStores: Boolean): List<MapPlace> =
        withContext(Dispatchers.IO) {
            val machines = request(machineQuery(bbox(lat, lon, 15.0)), isStore = false)
            val stores = if (includeStores) request(storeQuery(bbox(lat, lon, 3.0)), isStore = true) else emptyList()
            machines + stores
        }

    private fun bbox(lat: Double, lon: Double, km: Double): String {
        val dLat = km / 111.0
        val dLon = km / (111.0 * cos(Math.toRadians(lat)))
        return "%.5f,%.5f,%.5f,%.5f".format(Locale.US, lat - dLat, lon - dLon, lat + dLat, lon + dLon)
    }

    private fun machineQuery(box: String) = """
        [out:json][timeout:25];
        (
          nwr["vending"="bottle_return"]($box);
          nwr["recycling_type"="reverse_vending_machine"]($box);
        );
        out center tags 100;
    """.trimIndent()

    private fun storeQuery(box: String) = """
        [out:json][timeout:25];
        nwr["shop"="supermarket"]($box);
        out center tags 120;
    """.trimIndent()

    private fun request(query: String, isStore: Boolean): List<MapPlace> {
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "KaucjaPRO/1.0 (Android)")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                try {
                    conn.outputStream.use {
                        it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray())
                    }
                    if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    return parse(body, isStore)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("brak odpowiedzi serwera")
    }

    private fun parse(json: String, isStore: Boolean): List<MapPlace> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<MapPlace>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val center = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.getDouble("lat") else center?.getDouble("lat") ?: continue
            val lon = if (e.has("lon")) e.getDouble("lon") else center?.getDouble("lon") ?: continue

            val name = tags.optString("name")
                .ifBlank { tags.optString("operator") }
                .ifBlank { tags.optString("brand") }
                .ifBlank { if (isStore) "Sklep" else "Kaucjomat" }

            val details = listOfNotNull(
                tags.optString("operator").takeIf { it.isNotBlank() && it != name },
                tags.optString("opening_hours").takeIf { it.isNotBlank() }?.let { "Godziny: $it" }
            ).joinToString("\n")

            out += MapPlace(e.optLong("id"), name, lat, lon, isStore, details)
        }
        return out
    }
}
