package com.nekosu.data.system

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Rewrites the package name (and optionally the application label) inside the
 * compiled binary AndroidManifest.xml without recompiling resources.
 */
object ManagerManifest {
    private val identitySuffixes = setOf(
        ".fileprovider",
        ".androidx-startup",
        ".webui",
        ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    )

    fun rename(xml: ByteArray, from: String, to: String, displayName: String? = null): ByteArray {
        require(validPackage(from) && validPackage(to) && from != to)
        displayName?.let { require(it == ManagerAppearance.validateName(it)) }
        val input = ByteBuffer.wrap(xml).order(ByteOrder.LITTLE_ENDIAN)
        require(xml.size >= 8 && input.getShort(0).toInt() == 3 && input.getInt(4) == xml.size)
        val output = ByteArrayOutputStream()
        val headerSize = input.getShort(2).toInt() and 0xffff
        require(headerSize in 8..xml.size)
        output.write(xml, 0, headerSize)
        var offset = headerSize
        var renamed = false
        var strings: List<String>? = null
        var labelChanged = false
        while (offset < xml.size) {
            require(offset <= xml.size - 8)
            val type = input.getShort(offset).toInt() and 0xffff
            val size = input.getInt(offset + 4)
            require(size >= 8 && size <= xml.size - offset)
            if (type == 1) {
                require(!renamed)
                val chunk = xml.copyOfRange(offset, offset + size)
                strings = ManagerStringPool.strings(chunk)
                var packageFound = false
                val pool = ManagerStringPool.rewrite(chunk, listOfNotNull(displayName)) { value ->
                    when {
                        value == from -> {
                            packageFound = true
                            to
                        }

                        value.startsWith(from) && value.removePrefix(from) in identitySuffixes ->
                            to + value.removePrefix(from)

                        else -> value
                    }
                }
                require(packageFound)
                output.write(pool)
                renamed = true
            } else {
                val chunk = xml.copyOfRange(offset, offset + size)
                if (type == 0x102 && displayName != null) {
                    val pool = requireNotNull(strings)
                    val node = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
                    require(size >= 36)
                    if (pool.getOrNull(node.getInt(20)) == "application") {
                        val start = 16 + (node.getShort(24).toInt() and 0xffff)
                        val attributeSize = node.getShort(26).toInt() and 0xffff
                        val count = node.getShort(28).toInt() and 0xffff
                        require(attributeSize >= 20 && start >= 36 &&
                            start.toLong() + attributeSize.toLong() * count <= size)
                        repeat(count) { index ->
                            val at = start + index * attributeSize
                            if (pool.getOrNull(node.getInt(at + 4)) == "label" &&
                                pool.getOrNull(node.getInt(at)) == "http://schemas.android.com/apk/res/android") {
                                node.putInt(at + 8, pool.size)
                                node.put(at + 15, 3.toByte())
                                node.putInt(at + 16, pool.size)
                                labelChanged = true
                            }
                        }
                    }
                }
                output.write(chunk)
            }
            offset += size
        }
        require(renamed && offset == xml.size && (displayName == null || labelChanged))
        return output.toByteArray().also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, it.size)
        }
    }

    fun validPackage(value: String): Boolean =
        value.length in 3..200 && Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+").matches(value)
}
