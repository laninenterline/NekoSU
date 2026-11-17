package com.nekosu.data.system

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal Android binary XML / resources.arsc string pool reader and rewriter.
 * Only the string pool chunk is understood; the rest of the file is copied as-is.
 */
internal object ManagerStringPool {
    private fun buffer(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    fun strings(bytes: ByteArray): List<String> {
        require(bytes.size >= 28)
        val pool = buffer(bytes)
        val header = pool.getShort(2).toInt() and 0xffff
        val count = pool.getInt(8)
        val styles = pool.getInt(12)
        val start = pool.getInt(20)
        val styleStart = pool.getInt(24)
        require(header >= 28 && header <= bytes.size && count in 1..100_000 && styles in 0..count)
        require(header.toLong() + (count.toLong() + styles) * 4 <= start && start <= bytes.size)
        require(styleStart == 0 || styleStart in start..bytes.size)
        val end = if (styleStart == 0) bytes.size else styleStart
        val utf8 = pool.getInt(16) and 0x100 != 0
        return List(count) { index ->
            val relative = pool.getInt(header + index * 4)
            require(relative >= 0 && relative < end - start)
            val cursor = pool.duplicate().order(ByteOrder.LITTLE_ENDIAN).apply {
                position(start + relative)
                limit(end)
            }
            if (utf8) {
                val chars = readLength(cursor, true)
                val length = readLength(cursor, true)
                require(length < cursor.remaining())
                val encoded = ByteArray(length).also(cursor::get)
                require(cursor.get().toInt() == 0)
                encoded.toString(Charsets.UTF_8).also { require(it.length == chars) }
            } else {
                val length = readLength(cursor, false)
                require(length.toLong() * 2 + 2 <= cursor.remaining())
                val encoded = ByteArray(length * 2).also(cursor::get)
                require(cursor.short.toInt() == 0)
                encoded.toString(Charsets.UTF_16LE)
            }
        }
    }

    fun rewrite(
        bytes: ByteArray,
        append: List<String> = emptyList(),
        transform: (String) -> String = { it },
    ): ByteArray {
        val original = strings(bytes)
        val values = original.map(transform) + append
        require(values.size <= 100_000)
        val pool = buffer(bytes)
        val header = pool.getShort(2).toInt() and 0xffff
        val styles = pool.getInt(12)
        val flags = pool.getInt(16)
        val oldStart = pool.getInt(20)
        val oldStyleStart = pool.getInt(24)
        require(styles == 0 || oldStyleStart != 0)
        repeat(styles) { index ->
            val relative = pool.getInt(header + original.size * 4 + index * 4)
            if (relative != -1) {
                require(original[index] == values[index]) { "A styled label cannot be replaced" }
                require(relative >= 0 && relative % 4 == 0 && relative < bytes.size - oldStyleStart)
                var position = oldStyleStart + relative
                while (true) {
                    require(position <= bytes.size - 4)
                    val name = pool.getInt(position)
                    if (name == -1) break
                    require(position <= bytes.size - 12 && name in original.indices)
                    val first = pool.getInt(position + 4)
                    val last = pool.getInt(position + 8)
                    require(first >= 0 && last >= first && last < original[index].length)
                    position += 12
                }
            }
        }
        val oldArraysEnd = header + (original.size + styles) * 4
        val newStart = oldStart + append.size * 4
        val data = ByteArrayOutputStream()
        val offsets = values.map { value ->
            val offset = data.size()
            val utf8 = flags and 0x100 != 0
            if (utf8) {
                val encoded = value.toByteArray(Charsets.UTF_8)
                writeLength(data, value.length, true)
                writeLength(data, encoded.size, true)
                data.write(encoded)
                data.write(0)
            } else {
                writeLength(data, value.length, false)
                data.write(value.toByteArray(Charsets.UTF_16LE))
                data.write(byteArrayOf(0, 0))
            }
            offset
        }
        while (data.size() % 4 != 0) data.write(0)
        val suffix = if (oldStyleStart == 0) byteArrayOf() else bytes.copyOfRange(oldStyleStart, bytes.size)
        return ByteBuffer.allocate(newStart + data.size() + suffix.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(bytes, 0, header)
            offsets.forEach(::putInt)
            put(bytes, header + original.size * 4, styles * 4)
            put(bytes, oldArraysEnd, oldStart - oldArraysEnd)
            put(data.toByteArray())
            put(suffix)
            putInt(4, capacity())
            putInt(8, values.size)
            putInt(16, flags and 1.inv())
            putInt(20, newStart)
            putInt(24, if (oldStyleStart == 0) 0 else newStart + data.size())
        }.array()
    }

    private fun readLength(buffer: ByteBuffer, utf8: Boolean): Int {
        require(buffer.remaining() >= if (utf8) 1 else 2)
        val first = if (utf8) buffer.get().toInt() and 0xff else buffer.short.toInt() and 0xffff
        val high = if (utf8) 0x80 else 0x8000
        if (first and high == 0) return first
        require(buffer.remaining() >= if (utf8) 1 else 2)
        val second = if (utf8) buffer.get().toInt() and 0xff else buffer.short.toInt() and 0xffff
        return ((first and (high - 1)) shl if (utf8) 8 else 16) or second
    }

    private fun writeLength(output: ByteArrayOutputStream, length: Int, utf8: Boolean) {
        require(length >= 0 && (!utf8 || length <= 0x7fff))
        if (utf8) {
            if (length >= 0x80) output.write((length shr 8) or 0x80)
            output.write(length and 0xff)
        } else {
            if (length >= 0x8000) {
                val first = (length ushr 16) or 0x8000
                output.write(first and 0xff)
                output.write(first shr 8)
            }
            output.write(length and 0xff)
            output.write((length shr 8) and 0xff)
        }
    }
}
