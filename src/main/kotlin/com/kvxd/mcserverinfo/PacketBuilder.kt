package com.kvxd.mcserverinfo

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class PacketBuilder {
    
    private val buffer = ByteArrayOutputStream()

    fun writeVarInt(value: Int): PacketBuilder {
        var remaining = value
        do {
            var temp = (remaining and 0x7F).toByte()
            remaining = remaining ushr 7
            if (remaining != 0) temp = (temp.toInt() or 0x80).toByte()
            buffer.write(temp.toInt())
        } while (remaining != 0)
        return this
    }

    fun writeString(value: String): PacketBuilder {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeVarInt(bytes.size)
        buffer.write(bytes)
        return this
    }

    fun writeShort(value: Int): PacketBuilder {
        buffer.write((value shr 8).toByte().toInt())
        buffer.write((value and 0xFF).toByte().toInt())
        return this
    }

    fun writeLong(value: Long): PacketBuilder {
        ByteBuffer.allocate(8).apply {
            putLong(value)
            rewind()
        }.array().forEach { buffer.write(it.toInt()) }
        return this
    }

    fun writeBoolean(value: Boolean): PacketBuilder {
        buffer.write(if (value) 0x01 else 0x00)
        return this
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}