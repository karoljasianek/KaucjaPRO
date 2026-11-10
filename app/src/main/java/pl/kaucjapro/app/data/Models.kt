package pl.kaucjapro.app.data

import java.math.RoundingMode
import java.util.Locale

/** Rodzaj opakowania. Kwoty domyślne bierzemy z [Settings]. */
enum class Pack(val label: String) {
    PET_CAN("Butelka PET (do 3 l) / puszka (do 1 l)"),
    GLASS("Butelka szklana (do 1,5 l)"),
    OTHER("Inne opakowanie (własna kwota)")
}

/** Skąd pochodzi wpis. */
enum class Kind(val label: String) {
    BOTTLE("Butelka / puszka"),
    RECEIPT("Paragon"),
    MANUAL("Wpis ręczny")
}

/** Jedna pozycja w historii. Kwoty trzymamy w groszach, żeby uniknąć błędów zaokrągleń. */
data class Entry(
    val id: Long,
    val kind: Kind,
    val title: String,
    val barcode: String?,
    val unitCents: Int,
    val qty: Int,
    val createdAt: Long,
    val returned: Boolean = false
) {
    val cents: Int get() = unitCents * qty
}

/** Zapamiętany produkt (kod kreskowy -> nazwa, typ opakowania, kaucja). */
data class Product(
    val barcode: String,
    val name: String,
    val pack: Pack,
    val unitCents: Int
)

/** Domyślne stawki kaucji w groszach (PL: 0,50 zł PET/puszka, 1,00 zł szkło). */
data class Settings(
    val petCanCents: Int = 50,
    val glassCents: Int = 100
)

fun Settings.centsFor(pack: Pack): Int = when (pack) {
    Pack.PET_CAN -> petCanCents
    Pack.GLASS -> glassCents
    Pack.OTHER -> petCanCents
}

private val PL: Locale = Locale.forLanguageTag("pl-PL")

/** 150 -> "1,50 zł" */
fun Int.formatZl(): String = String.format(PL, "%.2f zł", this / 100.0)

/** 150 -> "1,50" (do pól tekstowych) */
fun Int.toInputString(): String = String.format(PL, "%d,%02d", this / 100, this % 100)

/** "1,50" / "1.5" -> 150. Zwraca null dla błędnego lub ujemnego wejścia. */
fun parseCents(input: String): Int? {
    val value = input.trim().replace(" ", "").replace(',', '.').toBigDecimalOrNull() ?: return null
    if (value.signum() < 0) return null
    return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt()
}
