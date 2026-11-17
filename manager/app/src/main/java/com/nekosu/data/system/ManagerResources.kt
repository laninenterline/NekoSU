package com.nekosu.data.system

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Rewrites the default application label string inside resources.arsc.
 */
object ManagerResources {
    /**
     * Point a file-backed resource at a different APK entry path. Used when a
     * .xml drawable (vector or adaptive icon) is replaced by a bitmap, because
     * Resources decides by file extension whether to parse XML or decode a bitmap.
     */
    fun renameFile(table: ByteArray, previous: String, replacement: String): ByteArray {
        require(previous.isNotEmpty() && replacement.isNotEmpty() && previous != replacement)
        val input = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
        require(table.size >= 12 && input.getShort(0).toInt() == 2 && input.getInt(4) == table.size)
        val header = input.getShort(2).toInt() and 0xffff
        require(header in 12..table.size)
        val output = ByteArrayOutputStream()
        output.write(table, 0, header)
        var offset = header
        var rewritten = false
        while (offset < table.size) {
            require(offset <= table.size - 8)
            val size = input.getInt(offset + 4)
            require(size >= 8 && size <= table.size - offset)
            if (!rewritten && input.getShort(offset).toInt() == 1) {
                output.write(ManagerStringPool.rewrite(table.copyOfRange(offset, offset + size)) {
                    if (it == previous) replacement else it
                })
                rewritten = true
            } else {
                output.write(table, offset, size)
            }
            offset += size
        }
        require(rewritten && offset == table.size)
        return output.toByteArray().also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, it.size)
        }
    }

    fun renameLabel(table: ByteArray, previous: String, replacement: String): ByteArray {
        require(previous.isNotEmpty() && replacement == ManagerAppearance.validateName(replacement))
        val input = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
        require(table.size >= 12 && input.getShort(0).toInt() == 2 && input.getInt(4) == table.size)
        val header = input.getShort(2).toInt() and 0xffff
        require(header in 12..table.size)
        val output = ByteArrayOutputStream()
        output.write(table, 0, header)
        var offset = header
        var rewritten = false
        while (offset < table.size) {
            require(offset <= table.size - 8)
            val size = input.getInt(offset + 4)
            require(size >= 8 && size <= table.size - offset)
            if (!rewritten && input.getShort(offset).toInt() == 1) {
                output.write(ManagerStringPool.rewrite(table.copyOfRange(offset, offset + size)) {
                    if (it == previous) replacement else it
                })
                rewritten = true
            } else {
                output.write(table, offset, size)
            }
            offset += size
        }
        require(rewritten && offset == table.size)
        return output.toByteArray().also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, it.size)
        }
    }
}
