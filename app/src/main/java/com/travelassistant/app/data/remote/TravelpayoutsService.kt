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
 * Client for the Travelpayouts (Aviasales) free flight-price data API. Token-only auth.
 *
 * Two endpoints are used, for two different needs:
 *  - Calendar of Prices ([calendarForMonth]) → the cheapest price for EACH day of a month, which
 *    gives a dense price curve for the chart.
 *  - Prices for Dates ([pricesForMonth]) → cheapest tickets with airline + deep link, used for the
 *    per-airline breakdown and "buy" links.
 */
class TravelpayoutsService(private val token: String) {
    private val base = "https://api.travelpayouts.com"
    private val json = Json { ignoreUnknownKeys = true }

    /** Cheapest price per day for [month] (yyyy-MM). Dense — one entry per covered day. */
    suspend fun calendarForMonth(
        origin: String,
        destination: String,
        month: String,
        currency: String,
    ): List<DayPrice> {
        val url = "$base/v1/prices/calendar" +
            "?depart_date=${enc(month)}&origin=${enc(origin)}&destination=${enc(destination)}" +
            "&calendar_type=departure_date&currency=${enc(currency)}&token=${enc(token)}"
        val body = httpGet(url)
        val parsed = runCatching { json.decodeFromString(CalendarResponse.serializer(), body) }.getOrNull()
            ?: return emptyList()
        if (!parsed.success) return emptyList()
        return parsed.data.mapNotNull { (dateKey, entry) ->
            val price = entry.value.takeIf { it > 0.0 } ?: entry.price.takeIf { it > 0.0 } ?: return@mapNotNull null
            val date = parseDate(dateKey) ?: parseDate(entry.depart_date) ?: return@mapNotNull null
            DayPrice(date, price)
        }
    }

    /** Cheapest cached tickets departing in [month] (yyyy-MM), with airline and deep link. */
    suspend fun pricesForMonth(
        origin: String,
        destination: String,
        month: String,
        currency: String,
    ): List<Ticket> {
        val url = "$base/aviasales/v3/prices_for_dates" +
            "?origin=${enc(origin)}&destination=${enc(destination)}" +
            "&departure_at=${enc(month)}&currency=${enc(currency)}" +
            "&one_way=true&sorting=price&limit=1000&market=br&token=${enc(token)}"
        val body = httpGet(url)
        val parsed = json.decodeFromString(PricesForDatesResponse.serializer(), body)
        if (!parsed.success) return emptyList()
        return parsed.data.mapNotNull { t ->
            if (t.price <= 0.0) return@mapNotNull null
            val date = parseDate(t.departure_at) ?: return@mapNotNull null
            Ticket(date, t.price, t.airline, t.link.ifBlank { null })
        }
    }

    private suspend fun httpGet(urlString: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
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

data class DayPrice(val date: LocalDate, val price: Double)

data class Ticket(
    val date: LocalDate,
    val price: Double,
    val airline: String,
    /** Aviasales search path (relative), or null. */
    val link: String? = null,
)

@Serializable
private data class CalendarResponse(
    val success: Boolean = false,
    val data: Map<String, CalendarEntryDto> = emptyMap(),
    val error: String? = null,
)

@Serializable
private data class CalendarEntryDto(
    val value: Double = 0.0,
    val price: Double = 0.0,
    val depart_date: String = "",
)

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
    val link: String = "",
)
