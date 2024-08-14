package no.nav.hjelpemidler.soknad.mottak.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems

fun jsonMessageOf(vararg pairs: Pair<String, Any?>): JsonMessage =
    JsonMessage("{}", MessageProblems("")).apply {
        pairs
            .mapNotNull { (key, value) -> if (value == null) null else key to value }
            .forEach { (key, value) -> this[key] = value }
    }
