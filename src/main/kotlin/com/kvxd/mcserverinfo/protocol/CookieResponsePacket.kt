package com.kvxd.mcserverinfo.protocol

import com.kvxd.mcserverinfo.PacketBuilder

class CookieResponsePacket(private val key: String) {
    
    fun toByteArray(): ByteArray {
        return PacketBuilder()
            .writeVarInt(0x04)
            .writeString(key)
            .writeBoolean(false)
            .toByteArray()
    }
    
}