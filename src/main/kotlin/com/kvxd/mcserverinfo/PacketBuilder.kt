package com.kvxd.mcserverinfo

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

class PacketBuilder {
    
    private val byteArrayOutputStream = ByteArrayOutputStream()
    private val dataOutputStream = DataOutputStream(byteArrayOutputStream)

    fun writeVarInt(value: Int): PacketBuilder {
        var remainingValue = value
        while (true) {
            if ((remainingValue and 0x7F) == remainingValue) {
                dataOutputStream.writeByte(remainingValue)
                return this
            }
            dataOutputStream.writeByte((remainingValue and 0x7F) or 0x80)
            remainingValue = remainingValue ushr 7
        }
    }

    fun writeString(value: String): PacketBuilder {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeVarInt(bytes.size)
        dataOutputStream.write(bytes)
        return this
    }

    fun writeShort(value: Int): PacketBuilder {
        dataOutputStream.writeShort(value)
        return this
    }

    fun writeLong(value: Long): PacketBuilder {
        dataOutputStream.writeLong(value)
        return this
    }
    
    fun writeBoolean(value: Boolean): PacketBuilder {
        dataOutputStream.writeBoolean(value)
        return this
    }

    fun toByteArray(): ByteArray = byteArrayOutputStream.toByteArray()
}