package pl.kaucjapro.app.ocr

/**
 * Wyciąga z tekstu paragonu (wynik OCR) pozycje kaucji.
 *
 * Reguły:
 *  - bierzemy linie zawierające "kaucj" (kaucja, kaucji...),
 *  - pomijamy linie zwrotu kaucji i kwoty ujemne,
 *  - kwota linii to ostatnia kwota w linii; jeśli jest tylko jedna kwota i ilość ("3 x" / "x 3"),
 *    mnożymy,
 *  - jeśli w linii nie ma kwoty, a następna linia to same liczby, bierzemy ją stamtąd.
 *
 * OCR bywa nieidealny, dlatego aplikacja zawsze pokazuje wynik do sprawdzenia przed zapisem.
 */
object ReceiptParser {

    data class Line(val text: String, val cents: Int)

    data class Result(val lines: List<Line>, val raw: String) {
        val totalCents: Int get() = lines.sumOf { it.cents }
    }

    private val amountRe = Regex("""(\d{1,4})\s?[,.]\s?(\d{2})(?!\d)""")
    private val qtyBeforeRe = Regex("""(?<![\d,.])(\d{1,3})\s*[x*×]""", RegexOption.IGNORE_CASE)
    private val qtyAfterRe = Regex("""[x*×]\s*(\d{1,3})(?![\d,.])""", RegexOption.IGNORE_CASE)
    private val refundRe = Regex(
        """zwrot(?!n)\w*\s+kaucj|kaucj\w*\s+zwrot(?!n)|-\s?\d+\s?[,.]\s?\d{2}""",
        RegexOption.IGNORE_CASE
    )
    private val wordRe = Regex("""[A-Za-zĄĆĘŁŃÓŚŹŻąćęłńóśźż]{3,}""")

    private const val MAX_CENTS = 20_000

    fun parse(text: String): Result {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val found = mutableListOf<Line>()

        for ((i, line) in lines.withIndex()) {
            if (!line.contains("kaucj", ignoreCase = true)) continue
            if (refundRe.containsMatchIn(line)) continue

            var source = line
            var amounts = amountsIn(line)
            if (amounts.isEmpty() && i + 1 < lines.size) {
                val next = lines[i + 1]
                if (!wordRe.containsMatchIn(next)) {
                    source = "$line $next"
                    amounts = amountsIn(next)
                }
            }
            if (amounts.isEmpty()) continue

            val qty = (qtyBeforeRe.find(source) ?: qtyAfterRe.find(source))
                ?.groupValues?.get(1)?.toIntOrNull()
            val cents = if (amounts.size == 1 && qty != null && qty > 1) amounts[0] * qty else amounts.last()

            if (cents in 1..MAX_CENTS) found += Line(line, cents)
        }
        return Result(found, text)
    }

    private fun amountsIn(s: String): List<Int> =
        amountRe.findAll(s)
            .map { it.groupValues[1].toInt() * 100 + it.groupValues[2].toInt() }
            .toList()
}
