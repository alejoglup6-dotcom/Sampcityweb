package com.sampcity.social.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// El servidor (mysql2 con bigNumberStrings) manda los conteos como texto ("3") y los 0/1 como número o booleano:
// estos serializadores aceptan cualquiera de las formas.
private fun Decoder.prim(): JsonPrimitive? = (this as JsonDecoder).decodeJsonElement() as? JsonPrimitive

private fun JsonPrimitive.num(): Long =
    if (this is JsonNull) 0 else longOrNull ?: doubleOrNull?.toLong() ?: booleanOrNull?.let { if (it) 1L else 0L } ?: 0L

object FI : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FI", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int = decoder.prim()?.num()?.toInt() ?: 0
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

object FL : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FL", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long = decoder.prim()?.num() ?: 0L
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

object FB : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FB", PrimitiveKind.BOOLEAN)
    override fun deserialize(decoder: Decoder): Boolean {
        val p = decoder.prim() ?: return false
        return p.booleanOrNull ?: (p.num() != 0L)
    }
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

/** Texto que puede llegar como número (la skin) o nulo. */
@OptIn(ExperimentalSerializationApi::class)
object FS : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FS", PrimitiveKind.STRING).nullable
    override fun deserialize(decoder: Decoder): String? {
        val p = decoder.prim() ?: return null
        return if (p is JsonNull) null else p.content
    }
    override fun serialize(encoder: Encoder, value: String?) = if (value == null) encoder.encodeNull() else encoder.encodeString(value)
}
