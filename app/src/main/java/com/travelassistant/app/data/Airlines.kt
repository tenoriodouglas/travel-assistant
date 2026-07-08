package com.travelassistant.app.data

/** Maps IATA airline codes to friendly names; unknown codes are shown as-is. */
object Airlines {
    private val NAMES = mapOf(
        "LA" to "LATAM", "JJ" to "LATAM", "G3" to "GOL", "AD" to "Azul", "TP" to "TAP",
        "AA" to "American", "UA" to "United", "DL" to "Delta", "AF" to "Air France",
        "KL" to "KLM", "IB" to "Iberia", "BA" to "British Airways", "LH" to "Lufthansa",
        "AR" to "Aerolíneas", "AV" to "Avianca", "CM" to "Copa", "AC" to "Air Canada",
        "AZ" to "ITA Airways", "UX" to "Air Europa", "EK" to "Emirates", "AT" to "Royal Air Maroc",
        "O6" to "Avianca Brasil", "2Z" to "Voepass", "9R" to "Satena", "H2" to "Sky Airline",
        "JA" to "JetSMART", "AM" to "Aeroméxico", "B6" to "JetBlue", "TK" to "Turkish",
    )

    fun name(code: String): String = NAMES[code] ?: code
}
