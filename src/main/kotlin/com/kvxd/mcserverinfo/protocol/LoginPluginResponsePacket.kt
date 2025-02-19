package com.kvxd.mcserverinfo.protocol

import com.kvxd.mcserverinfo.PacketBuilder

class LoginPluginResponsePacket(private val messageId: Int) {
    
    fun toByteArray(): ByteArray {
        return PacketBuilder()
            .writeVarInt(0x02)
            .writeVarInt(messageId)
            .writeBoolean(false)
            .toByteArray()
    }
    
}