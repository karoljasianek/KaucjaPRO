package pl.kaucjapro.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.kaucjapro.app.data.Entry
import pl.kaucjapro.app.data.Kind
import pl.kaucjapro.app.data.Pack
import pl.kaucjapro.app.data.Product
import pl.kaucjapro.app.data.Settings
import pl.kaucjapro.app.data.Store
import pl.kaucjapro.app.net.ProductLookup

class KaucjaViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    val entries: StateFlow<List<Entry>> = store.entries
    val settings: StateFlow<Settings> = store.settings

    /** Nieznany produkt czekający na potwierdzenie typu opakowania przez użytkownika. */
    data class Pending(val barcode: String, val suggestedName: String?, val suggestedPack: Pack?)

    /** Ostatnio dodana butelka (do banera z przyciskiem "Cofnij"). */
    data class LastAdd(val entryId: Long, val title: String, val unitCents: Int, val totalQty: Int)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    private val _lastAdd = MutableStateFlow<LastAdd?>(null)
    val lastAdd: StateFlow<LastAdd?> = _lastAdd.asStateFlow()

    private val _lookingUp = MutableStateFlow(false)
    val lookingUp: StateFlow<Boolean> = _lookingUp.asStateFlow()

    private var lastCode: String? = null
    private var lastTime = 0L

    /**
     * Wywoływane przez skaner dla każdego wykrytego kodu kreskowego.
     * Ten sam kod jest ignorowany przez 3 s (żeby jedna butelka nie dodała się wielokrotnie),
     * inny kod przez 1 s.
     */
    fun onBarcode(code: String) {
        if (_pending.value != null || _lookingUp.value) return
        val now = SystemClock.elapsedRealtime()
        val gap = if (code == lastCode) 3000L else 1000L
        if (now - lastTime < gap) return
        lastCode = code
        lastTime = now

        _lookingUp.value = true
        viewModelScope.launch {
            try {
                val known = store.product(code)
                if (known != null) {
                    addProduct(known)
                } else {
                    val guess = ProductLookup.fetch(code)
                    _pending.value = Pending(code, guess?.name, guess?.pack)
                }
            } finally {
                _lookingUp.value = false
            }
        }
    }

    fun confirmPending(name: String, pack: Pack, cents: Int) {
        val p = _pending.value ?: return
        _pending.value = null
        lastCode = p.barcode
        lastTime = SystemClock.elapsedRealtime()
        addProduct(
            Product(
                barcode = p.barcode,
                name = name.ifBlank { "Opakowanie ${p.barcode}" },
                pack = pack,
                unitCents = cents
            )
        )
    }

    fun cancelPending() {
        _pending.value = null
        lastTime = SystemClock.elapsedRealtime()
    }

    private fun addProduct(product: Product) {
        val e = store.addBottle(product)
        _lastAdd.value = LastAdd(e.id, e.title, e.unitCents, e.qty)
    }

    fun undoLast() {
        val last = _lastAdd.value ?: return
        store.changeQty(last.entryId, -1)
        _lastAdd.value = if (last.totalQty > 1) last.copy(totalQty = last.totalQty - 1) else null
    }

    fun addReceipt(title: String, cents: Int) = store.addAmount(Kind.RECEIPT, title, cents)

    fun addManual(title: String, cents: Int) = store.addAmount(Kind.MANUAL, title, cents)

    fun toggleReturned(entry: Entry) = store.setReturned(entry.id, !entry.returned)

    fun markAllReturned() = store.markAllReturned()

    fun delete(id: Long) = store.delete(id)

    fun saveSettings(settings: Settings) = store.saveSettings(settings)
}
