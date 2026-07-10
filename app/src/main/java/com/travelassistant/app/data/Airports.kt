package com.travelassistant.app.data

import com.travelassistant.app.data.model.Airport
import java.text.Normalizer
import java.util.Locale

/** Static airport catalog + accent-insensitive search powering the autocomplete. */
object Airports {

    val ALL: List<Airport> = listOf(
        // ---- Brasil ----
        Airport("GRU", "São Paulo", "Brasil", "BR", "guarulhos sp saopaulo"),
        Airport("CGH", "São Paulo", "Brasil", "BR", "congonhas sp saopaulo"),
        Airport("VCP", "Campinas", "Brasil", "BR", "viracopos sp"),
        Airport("GIG", "Rio de Janeiro", "Brasil", "BR", "galeao rj"),
        Airport("SDU", "Rio de Janeiro", "Brasil", "BR", "santos dumont rj"),
        Airport("BSB", "Brasília", "Brasil", "BR", "brasilia df"),
        Airport("CNF", "Belo Horizonte", "Brasil", "BR", "confins bh mg"),
        Airport("CWB", "Curitiba", "Brasil", "BR", "parana pr"),
        Airport("POA", "Porto Alegre", "Brasil", "BR", "rs riograndedosul"),
        Airport("REC", "Recife", "Brasil", "BR", "pe pernambuco"),
        Airport("SSA", "Salvador", "Brasil", "BR", "ba bahia"),
        Airport("FOR", "Fortaleza", "Brasil", "BR", "ce ceara"),
        Airport("BEL", "Belém", "Brasil", "BR", "pa para"),
        Airport("MAO", "Manaus", "Brasil", "BR", "am amazonas"),
        Airport("FLN", "Florianópolis", "Brasil", "BR", "floripa sc"),
        Airport("NAT", "Natal", "Brasil", "BR", "rn riograndedonorte"),
        Airport("MCZ", "Maceió", "Brasil", "BR", "al alagoas"),
        Airport("VIX", "Vitória", "Brasil", "BR", "es espiritosanto"),
        Airport("GYN", "Goiânia", "Brasil", "BR", "go goias"),
        Airport("SLZ", "São Luís", "Brasil", "BR", "ma maranhao"),
        Airport("JPA", "João Pessoa", "Brasil", "BR", "pb paraiba"),
        Airport("AJU", "Aracaju", "Brasil", "BR", "se sergipe"),
        Airport("PMW", "Palmas", "Brasil", "BR", "to tocantins"),
        Airport("CGB", "Cuiabá", "Brasil", "BR", "mt matogrosso"),
        // ---- América do Sul ----
        Airport("EZE", "Buenos Aires", "Argentina", "AR", "ezeiza"),
        Airport("AEP", "Buenos Aires", "Argentina", "AR", "aeroparque"),
        Airport("SCL", "Santiago", "Chile", "CL", "chile"),
        Airport("MVD", "Montevidéu", "Uruguai", "UY", "montevideo uruguai"),
        Airport("ASU", "Assunção", "Paraguai", "PY", "asuncion paraguai"),
        Airport("LIM", "Lima", "Peru", "PE", "peru"),
        Airport("BOG", "Bogotá", "Colômbia", "CO", "bogota colombia"),
        // ---- América do Norte / Central ----
        Airport("JFK", "Nova York", "EUA", "US", "newyork nyc kennedy"),
        Airport("EWR", "Newark", "EUA", "US", "newyork nyc"),
        Airport("MIA", "Miami", "EUA", "US", "florida"),
        Airport("MCO", "Orlando", "EUA", "US", "florida"),
        Airport("LAX", "Los Angeles", "EUA", "US", "california"),
        Airport("IAH", "Houston", "EUA", "US", "texas"),
        Airport("ATL", "Atlanta", "EUA", "US", "georgia"),
        Airport("YYZ", "Toronto", "Canadá", "CA", "canada"),
        Airport("MEX", "Cidade do México", "México", "MX", "mexico ciudad"),
        Airport("CUN", "Cancún", "México", "MX", "cancun mexico"),
        Airport("PTY", "Cidade do Panamá", "Panamá", "PA", "panama"),
        // ---- Europa ----
        Airport("LIS", "Lisboa", "Portugal", "PT", "lisbon portugal"),
        Airport("OPO", "Porto", "Portugal", "PT", "oporto portugal"),
        Airport("MAD", "Madri", "Espanha", "ES", "madrid espanha"),
        Airport("BCN", "Barcelona", "Espanha", "ES", "espanha"),
        Airport("CDG", "Paris", "França", "FR", "paris franca charlesdegaulle"),
        Airport("ORY", "Paris", "França", "FR", "paris franca orly"),
        Airport("LHR", "Londres", "Reino Unido", "GB", "london heathrow"),
        Airport("LGW", "Londres", "Reino Unido", "GB", "london gatwick"),
        Airport("FCO", "Roma", "Itália", "IT", "rome italia fiumicino"),
        Airport("MXP", "Milão", "Itália", "IT", "milan italia malpensa"),
        Airport("FRA", "Frankfurt", "Alemanha", "DE", "germany alemanha"),
        Airport("MUC", "Munique", "Alemanha", "DE", "munich alemanha"),
        Airport("AMS", "Amsterdã", "Holanda", "NL", "amsterdam holanda"),
        Airport("ZRH", "Zurique", "Suíça", "CH", "zurich suica"),
        Airport("DUB", "Dublin", "Irlanda", "IE", "ireland irlanda"),
    ).distinctBy { it.code }

    private val byCode = ALL.associateBy { it.code }

    fun byCode(code: String): Airport? = byCode[code]

    /** Search by IATA code, city name or keyword, ignoring accents and case. */
    fun search(query: String, limit: Int = 8): List<Airport> {
        val q = normalize(query).trim()
        if (q.isEmpty()) return emptyList()
        return ALL.asSequence()
            .mapNotNull { airport ->
                val code = airport.code.lowercase(Locale.ROOT)
                val city = normalize(airport.city)
                val keywords = normalize(airport.keywords)
                val score = when {
                    code == q -> 0
                    code.startsWith(q) -> 1
                    city.startsWith(q) -> 2
                    city.contains(q) -> 3
                    keywords.contains(q) -> 4
                    else -> -1
                }
                if (score >= 0) airport to score else null
            }
            .sortedWith(compareBy({ it.second }, { it.first.city }))
            .map { it.first }
            .take(limit)
            .toList()
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)
}
