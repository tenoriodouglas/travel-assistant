package com.travelassistant.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/** Thin client for the Amadeus Self-Service flight-price APIs (OAuth2 + two endpoints). */
class AmadeusService(
    private val clientId: String,
    private val clientSecret: String,
    environment: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val base =
        if (environment.equals("production", ignoreCase = true)) "https://api.amadeus.com"
        else "https://test.api.amadeus.com"

    private val json = Json { ignoreUnknownKeys = true }
    private val tokenMutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAt = 0L

    /** Cheapest price per departure date for a route (may be empty — coverage is partial). */
    suspend fun cheapestDates(origin: String, destination: String, currency: String): List<DatePrice> {
        val token = ensureToken()
        val url = "$base/v1/shopping/flight-dates?" +
            "origin=${enc(origin)}&destination=${enc(destination)}&oneWay=true&currencyCode=${enc(currency)}"
        val body = httpGet(url, token)
        val parsed = json.decodeFromString(FlightDatesResponse.serializer(), body)
        return parsed.data.mapNotNull { d ->
            val price = d.price.total.toDoubleOrNull() ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(d.departureDate) }.getOrNull() ?: return@mapNotNull null
            DatePrice(date, price)
        }
    }

    /** Per-airline offers for a specific departure date; returns (airlineCode, totalPrice). */
    suspend fun offers(
        origin: String,
        destination: String,
        date: LocalDate,
        currency: String,
        max: Int = 20,
    ): List<AirlinePrice> {
        val token = ensureToken()
        val url = "$base/v2/shopping/flight-offers?" +
            "originLocationCode=${enc(origin)}&destinationLocationCode=${enc(destination)}" +
            "&departureDate=$date&adults=1&currencyCode=${enc(currency)}&max=$max"
        val body = httpGet(url, token)
        val parsed = json.decodeFromString(FlightOffersResponse.serializer(), body)
        return parsed.data.mapNotNull { offer ->
            val price = offer.price.total.toDoubleOrNull() ?: return@mapNotNull null
            val airline = offer.validatingAirlineCodes.firstOrNull() ?: return@mapNotNull null
            AirlinePrice(airline, price)
        }
    }

    private suspend fun ensureToken(): String = tokenMutex.withLock {
        val now = clock()
        cachedToken?.let { if (now < tokenExpiresAt) return it }
        val form = "grant_type=client_credentials" +
            "&client_id=${enc(clientId)}&client_secret=${enc(clientSecret)}"
        val body = httpPostForm("$base/v1/security/oauth2/token", form)
        val token = json.decodeFromString(TokenResponse.serializer(), body)
        cachedToken = token.access_token
        tokenExpiresAt = now + (token.expires_in - 60).coerceAtLeast(30) * 1000L
        token.access_token
    }

    private suspend fun httpGet(urlString: String, token: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        conn.readOrThrow()
    }

    private suspend fun httpPostForm(urlString: String, form: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        conn.outputStream.use { it.write(form.toByteArray()) }
        conn.readOrThrow()
    }

    private fun HttpURLConnection.readOrThrow(): String {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IOException("Amadeus HTTP $code: ${text.take(200)}")
        }
        return text
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}

data class DatePrice(val date: LocalDate, val price: Double)
data class AirlinePrice(val airlineCode: String, val price: Double)

@Serializable
private data class TokenResponse(val access_token: String, val expires_in: Int)

@Serializable
private data class FlightDatesResponse(val data: List<FlightDateDto> = emptyList())

@Serializable
private data class FlightDateDto(val departureDate: String, val price: PriceDto)

@Serializable
private data class FlightOffersResponse(val data: List<FlightOfferDto> = emptyList())

@Serializable
private data class FlightOfferDto(
    val price: PriceDto,
    val validatingAirlineCodes: List<String> = emptyList(),
)

@Serializable
private data class PriceDto(val total: String = "", val currency: String = "")
