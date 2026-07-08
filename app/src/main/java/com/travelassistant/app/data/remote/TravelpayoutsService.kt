package com.travelassistant.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Client for the Travelpayouts (Aviasales) free flight-price data API. Token-only auth
 * (no OAuth). The Prices-for-Dates endpoint returns the cheapest cached tickets for a month,
 * which we group by departure date into a price curve.
 */
class TravelpayoutsService(private val token: String) {
    private val base = "https://api.travelpayouts.com"
    private val json = Json { ignoreUnknownKeys = true }

    /** Cheapest cached tickets departing in [month] (format yyyy-MM). */
    suspend fun pricesForMonth(
        origin: String,
        destination: String,
        month: String,
        currency: String,
    ): List<Ticket> {
        val url = "$base/aviasales/v3/prices_for_dates" +
            "?origin=${enc(origin)}&destination=${enc(destination)}" +
            "&departure_at=${enc(month)}&currency=${enc(currency)}" +
            "&one_way=true&sorting=price&limit=1000&market=br"
        val body = httpGet(url)
        val parsed = json.decodeFromString(PricesForDatesResponse.serializer(), body)
        if (!parsed.success) throw IOException("Travelpayouts: ${parsed.error ?: "resposta sem sucesso"}")
        return parsed.data.mapNotNull { t ->
            if (t.price <= 0.0) return@mapNotNull null
            val date = parseDate(t.departure_at) ?: return@mapNotNull null
            Ticket(date, t.price, t.airline)
        }
    }

    private suspend fun httpGet(urlString: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            // Token via header keeps it out of URLs/logs.
            setRequestProperty("X-Access-Token", token)
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IOException("Travelpayouts HTTP $code: ${text.take(200)}")
        text
    }

    private fun parseDate(raw: String): LocalDate? {
        if (raw.length < 10) return null
        return runCatching { LocalDate.parse(raw.substring(0, 10)) }.getOrNull()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}

data class Ticket(val date: LocalDate, val price: Double, val airline: String)

@Serializable
private data class PricesForDatesResponse(
    val success: Boolean = false,
    val data: List<TicketDto> = emptyList(),
    val error: String? = null,
)

@Serializable
private data class TicketDto(
    val price: Double = 0.0,
    val airline: String = "",
    val departure_at: String = "",
)
