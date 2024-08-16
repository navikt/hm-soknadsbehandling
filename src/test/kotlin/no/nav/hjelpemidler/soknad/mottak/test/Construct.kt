package no.nav.hjelpemidler.soknad.mottak.test

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.typeOf

inline fun <reified T : Any> construct(): T {
    val id = UUID.randomUUID()
    val now = Instant.now()
    val zoneId = ZoneId.systemDefault()
    val constructor = T::class.primaryConstructor!!
    return constructor.callBy(constructor.parameters.associateWith {
        val name = it.name
        val type = it.type
        when {
            type.isSubtypeOf(typeOf<Boolean?>()) -> false
            type.isSubtypeOf(typeOf<Double?>()) -> 0.0
            type.isSubtypeOf(typeOf<Float?>()) -> 0.0F
            type.isSubtypeOf(typeOf<Instant?>()) -> now
            type.isSubtypeOf(typeOf<Int?>()) -> 0
            type.isSubtypeOf(typeOf<LocalDate?>()) -> LocalDate.ofInstant(now, zoneId)
            type.isSubtypeOf(typeOf<LocalDateTime?>()) -> LocalDateTime.ofInstant(now, zoneId)
            type.isSubtypeOf(typeOf<Long?>()) -> 0L
            type.isSubtypeOf(typeOf<OffsetDateTime?>()) -> TODO()
            type.isSubtypeOf(typeOf<String?>()) -> name ?: ""
            type.isSubtypeOf(typeOf<UUID?>()) -> id
            type.isSubtypeOf(typeOf<ZonedDateTime?>()) -> now.atZone(zoneId)
            else -> error("Mangler verdi for $name: $type")
        }
    })
}
