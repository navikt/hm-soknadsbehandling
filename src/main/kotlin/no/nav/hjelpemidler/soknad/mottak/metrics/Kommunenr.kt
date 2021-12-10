package no.nav.hjelpemidler.soknad.mottak.metrics

import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Source:
 * - https://www.kartverket.no/til-lands/kommunereform/tekniske-endringer-ved-sammenslaing-og-grensejustering/komendr2020
 *      Lagret og eksportert til .csv
 */
object Kommunenr {

    data class Sted(val kommunenavn: String, val fylkesnavn: String)

    private const val KOMMUNENR_FIL = "kommunenr/kommunenr.csv"
    private val kommunenrTilSted: MutableMap<String, Sted> = mutableMapOf()

    fun kommunenrTilSted(kommunenr: String?): Sted? {
        return kommunenrTilSted[kommunenr]
    }

    init {
        val csvSplitBy = ";"
        javaClass.classLoader.getResourceAsStream(KOMMUNENR_FIL).bufferedReader(StandardCharsets.UTF_8)
            .forEachLine { line ->
                val splitLine = line.split(csvSplitBy).toTypedArray()
                val kommunenr: String? = splitLine[6]
                val kommunenavn: String? = splitLine[7]
                val fylkesnavn: String? = splitLine[5]

                if (kommunenr != null && kommunenavn != null && fylkesnavn != null) {
                    kommunenrTilSted[kommunenr] = Sted(kommunenavn, fylkesnavn)
                } else {
                    throw IOException("There was an error parsing post data from file $KOMMUNENR_FIL for kommunenr $kommunenr")
                }
            }
    }
}
