package de.bjrke.factorio.optimizer.common.blueprint

/** CMF byte of the zlib header: DEFLATE with a 32K window. */
private const val ZLIB_CMF = 0x78

/**
 * FLG byte of the zlib header. FLEVEL (bits 6-7) is left at 0 ("fastest"), the dictionary flag is
 * unset and the header checksum works out because `0x7801 % 31 == 0`.
 */
private const val ZLIB_FLG = 0x01

/** A DEFLATE stored block carries a 16-bit length, so a chunk is at most 65535 bytes. */
private const val MAX_STORED_BLOCK = 65535

private const val ADLER_MOD = 65521

private const val BASE64_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/**
 * Encodes a blueprint [json] as a Factorio blueprint string: the version byte `0` followed by the
 * zlib-deflated JSON in base64. Factorio's importer treats DEFLATE's stored (uncompressed) blocks
 * like any other, so the compression is a pure-Kotlin, dependency-free passthrough; keeping the
 * JSON minimal is what keeps the string short. As of 2.0 the game also imports the raw JSON
 * directly, but the string is what blueprints are shared as.
 */
fun blueprintString(json: String): String = "0" + base64Encode(zlibStored(json.encodeToByteArray()))

/**
 * A valid zlib stream (RFC 1950) whose DEFLATE payload is a sequence of stored (BTYPE 00) blocks,
 * followed by the Adler-32 checksum of [data]. Empty input still emits one empty final block, which
 * is a legal (if trivial) DEFLATE stream.
 */
internal fun zlibStored(data: ByteArray): ByteArray {
    val out = ArrayList<Byte>(data.size + 16)
    out += ZLIB_CMF.toByte()
    out += ZLIB_FLG.toByte()

    var offset = 0
    do {
        val size = minOf(MAX_STORED_BLOCK, data.size - offset)
        val final = offset + size >= data.size
        out += (if (final) 1 else 0).toByte()
        // LEN followed by its one's complement, both little-endian
        out += (size and 0xFF).toByte()
        out += ((size shr 8) and 0xFF).toByte()
        val nlen = size.inv() and 0xFFFF
        out += (nlen and 0xFF).toByte()
        out += ((nlen shr 8) and 0xFF).toByte()
        for (index in offset until offset + size) out += data[index]
        offset += size
    } while (offset < data.size)

    val adler = adler32(data)
    out += ((adler shr 24) and 0xFF).toByte()
    out += ((adler shr 16) and 0xFF).toByte()
    out += ((adler shr 8) and 0xFF).toByte()
    out += (adler and 0xFF).toByte()
    return out.toByteArray()
}

/** The Adler-32 checksum zlib appends to its stream. */
internal fun adler32(data: ByteArray): Int {
    var a = 1
    var b = 0
    for (byte in data) {
        a = (a + (byte.toInt() and 0xFF)) % ADLER_MOD
        b = (b + a) % ADLER_MOD
    }
    return (b shl 16) or a
}

/** Standard base64 (RFC 4648) with `=` padding, matching the encoding a blueprint string uses. */
internal fun base64Encode(data: ByteArray): String {
    val result = StringBuilder((data.size + 2) / 3 * 4)
    var index = 0
    while (index + 3 <= data.size) {
        val chunk =
            ((data[index].toInt() and 0xFF) shl 16) or
                ((data[index + 1].toInt() and 0xFF) shl 8) or
                (data[index + 2].toInt() and 0xFF)
        result.append(BASE64_ALPHABET[(chunk shr 18) and 0x3F])
        result.append(BASE64_ALPHABET[(chunk shr 12) and 0x3F])
        result.append(BASE64_ALPHABET[(chunk shr 6) and 0x3F])
        result.append(BASE64_ALPHABET[chunk and 0x3F])
        index += 3
    }
    when (data.size - index) {
        1 -> {
            val chunk = (data[index].toInt() and 0xFF) shl 16
            result.append(BASE64_ALPHABET[(chunk shr 18) and 0x3F])
            result.append(BASE64_ALPHABET[(chunk shr 12) and 0x3F])
            result.append("==")
        }
        2 -> {
            val chunk =
                ((data[index].toInt() and 0xFF) shl 16) or
                    ((data[index + 1].toInt() and 0xFF) shl 8)
            result.append(BASE64_ALPHABET[(chunk shr 18) and 0x3F])
            result.append(BASE64_ALPHABET[(chunk shr 12) and 0x3F])
            result.append(BASE64_ALPHABET[(chunk shr 6) and 0x3F])
            result.append('=')
        }
    }
    return result.toString()
}
