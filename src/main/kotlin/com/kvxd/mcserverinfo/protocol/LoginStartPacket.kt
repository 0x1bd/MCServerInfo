package com.kvxd.mcserverinfo.protocol

import com.kvxd.mcserverinfo.PacketBuilder
import java.nio.charset.StandardCharsets
import java.util.UUID

class LoginStartPacket(
    private val username: String,
    private val protocolVersion: Int
) {
    
    fun toByteArray(): ByteArray {
        val uuid = if (protocolVersion >= 761) {
            UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8))
        } else {
            UUID(0, 0)
        }

        return PacketBuilder().apply {
            writeVarInt(0x00)
            writeString(username)
            if (protocolVersion >= 761) {
                writeLong(uuid.mostSignificantBits)
                writeLong(uuid.leastSignificantBits)
            }
        }.toByteArray()
    }
    
}