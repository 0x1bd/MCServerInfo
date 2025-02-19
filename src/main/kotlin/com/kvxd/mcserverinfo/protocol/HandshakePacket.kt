package com.kvxd.mcserverinfo.protocol

import com.kvxd.mcserverinfo.PacketBuilder

class HandshakePacket(
    private val protocolVersion: Int,
    private val address: String,
    private val port: Int,
    private val nextState: Int
) {
    
    fun toByteArray(): ByteArray {
        return PacketBuilder()
            .writeVarInt(0x00)
            .writeVarInt(protocolVersion)
            .writeString(address)
            .writeShort(port)
            .writeVarInt(nextState)
            .toByteArray()
    }
    
}