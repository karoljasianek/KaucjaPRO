package pl.kaucjapro.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Prosty magazyn danych: historia + katalog produktów w jednym pliku JSON w pamięci
 * aplikacji (nic nie opuszcza telefonu). Ustawienia stawek w SharedPreferences.
 */
class Store(context: Context) {

    private val file = File(context.filesDir, "kaucja_state.json")
    private val prefs = context.getSharedPreferences("kaucja_settings", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _products = MutableStateFlow<Map<String, Product>>(emptyMap())

    private val _settings = MutableStateFlow(
        Settings(
            petCanCents = prefs.getInt("pet_can_cents", 50),
            glassCents = prefs.getInt("glass_cents", 100)
        )
    )
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    init {
        load()
    }

    fun product(barcode: String): Product? = _products.value[barcode]

    /** Dodaje jedną sztukę. Jeśli ten sam produkt jest już na liście (niezwrócony), zwiększa ilość. */
    fun addBottle(product: Product): Entry {
        var result: Entry? = null
        _products.update { it + (product.barcode to product) }
        _entries.update { list ->
            val idx = list.indexOfFirst {
                it.kind == Kind.BOTTLE && !it.returned &&
                    it.barcode == product.barcode && it.unitCents == product.unitCents
            }
            if (idx >= 0) {
                val updated = list[idx].copy(qty = list[idx].qty + 1)
                result = updated
                list.toMutableList().also { it[idx] = updated }
            } else {
                val created = Entry(
                    id = (list.maxOfOrNull { it.id } ?: 0L) + 1,
                    kind = Kind.BOTTLE,
                    title = product.name,
                    barcode = product.barcode,
                    unitCents = product.unitCents,
                    qty = 1,
                    createdAt = System.currentTimeMillis()
                )
                result = created
                listOf(created) + list
            }
        }
        persist()
        return result!!
    }

    fun addAmount(kind: Kind, title: String, cents: Int) {
        _entries.update { list ->
            val created = Entry(
                id = (list.maxOfOrNull { it.id } ?: 0L) + 1,
                kind = kind,
                title = title,
                barcode = null,
                unitCents = cents,
                qty = 1,
                createdAt = System.currentTimeMillis()
            )
            listOf(created) + list
        }
        persist()
    }

    /** Zmienia ilość; przy wyniku <= 0 usuwa wpis. */
    fun changeQty(id: Long, delta: Int) {
        _entries.update { list ->
            list.mapNotNull { e ->
                if (e.id != id) e else {
                    val q = e.qty + delta
                    if (q <= 0) null else e.copy(qty = q)
                }
            }
        }
        persist()
    }

    fun setReturned(id: Long, returned: Boolean) {
        _entries.update { list -> list.map { if (it.id == id) it.copy(returned = returned) else it } }
        persist()
    }

    fun markAllReturned() {
        _entries.update { list -> list.map { it.copy(returned = true) } }
        persist()
    }

    fun delete(id: Long) {
        _entries.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun saveSettings(settings: Settings) {
        prefs.edit()
            .putInt("pet_can_cents", settings.petCanCents)
            .putInt("glass_cents", settings.glassCents)
            .apply()
        _settings.value = settings
    }

    // ---------- zapis / odczyt ----------

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val entriesJson = root.optJSONArray("entries") ?: JSONArray()
            _entries.value = (0 until entriesJson.length()).map { entryFromJson(entriesJson.getJSONObject(it)) }
            val productsJson = root.optJSONArray("products") ?: JSONArray()
            _products.value = (0 until productsJson.length())
                .map { productFromJson(productsJson.getJSONObject(it)) }
                .associateBy { it.barcode }
        } catch (e: Exception) {
            // Uszkodzony plik: zachowujemy kopię i startujemy od zera, zamiast się wywrócić.
            file.renameTo(File(file.parentFile, "kaucja_state.corrupt.json"))
        }
    }

    private fun persist() {
        scope.launch {
            mutex.withLock {
                val root = JSONObject()
                val entriesJson = JSONArray()
                _entries.value.forEach { entriesJson.put(entryToJson(it)) }
                val productsJson = JSONArray()
                _products.value.values.forEach { productsJson.put(productToJson(it)) }
                root.put("entries", entriesJson)
                root.put("products", productsJson)
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(root.toString())
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }

    private fun entryToJson(e: Entry) = JSONObject().apply {
        put("id", e.id)
        put("kind", e.kind.name)
        put("title", e.title)
        put("barcode", e.barcode ?: JSONObject.NULL)
        put("unitCents", e.unitCents)
        put("qty", e.qty)
        put("createdAt", e.createdAt)
        put("returned", e.returned)
    }

    private fun entryFromJson(o: JSONObject) = Entry(
        id = o.getLong("id"),
        kind = runCatching { Kind.valueOf(o.getString("kind")) }.getOrDefault(Kind.MANUAL),
        title = o.optString("title"),
        barcode = if (o.isNull("barcode")) null else o.getString("barcode"),
        unitCents = o.getInt("unitCents"),
        qty = o.getInt("qty"),
        createdAt = o.getLong("createdAt"),
        returned = o.optBoolean("returned", false)
    )

    private fun productToJson(p: Product) = JSONObject().apply {
        put("barcode", p.barcode)
        put("name", p.name)
        put("pack", p.pack.name)
        put("unitCents", p.unitCents)
    }

    private fun productFromJson(o: JSONObject) = Product(
        barcode = o.getString("barcode"),
        name = o.optString("name"),
        pack = runCatching { Pack.valueOf(o.getString("pack")) }.getOrDefault(Pack.OTHER),
        unitCents = o.getInt("unitCents")
    )
}
