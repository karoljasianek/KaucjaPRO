package pl.kaucjapro.app

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.kaucjapro.app.data.parseCents
import pl.kaucjapro.app.ocr.ReceiptParser

class ReceiptParserTest {

    @Test
    fun singleDepositLine() {
        val r = ReceiptParser.parse("MLEKO 3,29 A\nKAUCJA PET 0,50 B\nSUMA PLN 3,79")
        assertEquals(50, r.totalCents)
    }

    @Test
    fun quantityBeforeAmountWithLineTotal() {
        val r = ReceiptParser.parse("KAUCJA 3 x 0,50 1,50 A")
        assertEquals(150, r.totalCents)
    }

    @Test
    fun quantityAfterUnitPriceOnly() {
        val r = ReceiptParser.parse("Kaucja PET 0,50 x 3")
        assertEquals(150, r.totalCents)
    }

    @Test
    fun refundLineIsIgnored() {
        val r = ReceiptParser.parse("ZWROT KAUCJI -1,50\nKAUCJA 0,50")
        assertEquals(50, r.totalCents)
    }

    @Test
    fun amountOnNextLine() {
        val r = ReceiptParser.parse("KAUCJA PET\n0,50 B")
        assertEquals(50, r.totalCents)
    }

    @Test
    fun reusableBottleIsNotTreatedAsRefund() {
        val r = ReceiptParser.parse("Kaucja butelka zwrotna 1,00")
        assertEquals(100, r.totalCents)
    }

    @Test
    fun noDeposit() {
        val r = ReceiptParser.parse("CHLEB 4,50\nSUMA 4,50")
        assertEquals(0, r.totalCents)
    }

    @Test
    fun parseCentsAcceptsCommaAndDot() {
        assertEquals(150, parseCents("1,50"))
        assertEquals(150, parseCents("1.5"))
        assertEquals(null, parseCents("abc"))
        assertEquals(null, parseCents("-1"))
    }
}
