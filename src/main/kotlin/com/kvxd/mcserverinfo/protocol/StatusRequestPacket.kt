package com.kvxd.mcserverinfo.protocol

import com.kvxd.mcserverinfo.PacketBuilder

class StatusRequestPacket {
    
    fun toByteArray(): ByteArray {
        return PacketBuilder()
            .writeVarInt(0x00)
            .toByteArray()
    }
    
}