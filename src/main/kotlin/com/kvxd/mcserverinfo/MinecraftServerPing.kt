package com.kvxd.mcserverinfo

import com.google.gson.Gson
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

class MinecraftServerPing(
    private val serverAddress: String,
    private val serverPort: Int = 25565,
    private val timeout: Int = 5000,
    private val gson: Gson
) {
    private val protocolVersion: Int = -1 // Latest
    private val socket: Socket = Socket()

    fun ping(): ServerStatusResponse {
        socket.connect(InetSocketAddress(serverAddress, serverPort), timeout)

        val outputStream = DataOutputStream(socket.getOutputStream())
        val inputStream = DataInputStream(socket.getInputStream())

        try {
            sendHandshake(outputStream)

            sendStatusRequest(outputStream)

            val response = receiveStatusResponse(inputStream)

            println(response)

            return gson.fromJson(response, ServerStatusResponse::class.java )
        } catch (e: Exception) {
            throw IOException("Failed to ping server: ${e.message}", e)
        } finally {
            socket.close()
        }
    }

    private fun sendHandshake(outputStream: DataOutputStream) {
        val handshakePacket = PacketBuilder()
            .writeVarInt(0x00)
            .writeVarInt(protocolVersion)
            .writeString(serverAddress)
            .writeShort(serverPort)
            .writeVarInt(1) // 1 = Status

        writeVarInt(outputStream, handshakePacket.toByteArray().size)
        outputStream.write(handshakePacket.toByteArray())
    }

    private fun sendStatusRequest(outputStream: DataOutputStream) {
        val statusRequestPacket = PacketBuilder()
            .writeVarInt(0x00)

        writeVarInt(outputStream, statusRequestPacket.toByteArray().size)
        outputStream.write(statusRequestPacket.toByteArray())
    }

    private fun receiveStatusResponse(inputStream: DataInputStream): String {
        readVarInt(inputStream)
        val packetId = readVarInt(inputStream)

        if (packetId != 0x00) {
            throw IOException("Invalid packet ID: $packetId")
        }

        val jsonResponseLength = readVarInt(inputStream)
        val jsonResponse = ByteArray(jsonResponseLength)
        inputStream.readFully(jsonResponse)

        return String(jsonResponse, StandardCharsets.UTF_8)
    }

    private fun writeVarInt(outputStream: DataOutputStream, value: Int) {
        var remainingValue = value
        while (true) {
            if ((remainingValue and 0x7F) == remainingValue) {
                outputStream.writeByte(remainingValue)
                return
            }
            outputStream.writeByte((remainingValue and 0x7F) or 0x80)
            remainingValue = remainingValue ushr 7
        }
    }

    private fun readVarInt(inputStream: DataInputStream): Int {
        var value = 0
        var position = 0
        var byte: Int
        do {
            byte = inputStream.readByte().toInt()
            value = value or ((byte and 0x7F) shl position)
            position += 7
        } while (byte and 0x80 != 0)
        return value
    }
}