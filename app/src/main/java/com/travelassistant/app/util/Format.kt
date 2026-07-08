package com.travelassistant.app.util

import java.util.Locale
import kotlin.math.abs

private val ptBr = Locale("pt", "BR")

/** "R$ 4.120" style money, no decimals for the compact market rows. */
fun formatMoney(value: Double, currency: String = "R$"): String =
    "$currency ${String.format(ptBr, "%,.0f", value)}"

/** "R$ 4.120,50" with cents for the detail header. */
fun formatMoneyPrecise(value: Double, currency: String = "R$"): String =
    "$currency ${String.format(ptBr, "%,.2f", value)}"

fun formatPercent(pct: Double): String {
    val sign = if (pct >= 0) "+" else "-"
    return "$sign${String.format(ptBr, "%.2f", abs(pct))}%"
}

fun formatSignedMoney(value: Double, currency: String = "R$"): String {
    val sign = if (value >= 0) "+" else "-"
    return "$sign$currency ${String.format(ptBr, "%,.2f", abs(value))}"
}
