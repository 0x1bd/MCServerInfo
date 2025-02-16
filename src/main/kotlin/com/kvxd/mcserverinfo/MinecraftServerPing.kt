package com.kvxd.mcserverinfo

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
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

    fun ping(): ServerStatusResponse {
        val socket = Socket()
        socket.use { socket ->
            socket.connect(InetSocketAddress(serverAddress, serverPort), timeout)
            socket.soTimeout = timeout

            sendHandshake(socket)
            sendStatusRequest(socket)
            val response = receiveStatusResponse(socket)
            return gson.fromJson(response, ServerStatusResponse::class.java)
        }
    }

    private fun sendHandshake(socket: Socket) {
        val handshakePacket = PacketBuilder()
            .writeVarInt(0x00)
            .writeVarInt(protocolVersion)
            .writeString(serverAddress)
            .writeShort(serverPort)
            .writeVarInt(1) // 1 = Status
            .toByteArray()

        val outputStream = socket.getOutputStream()
        writeVarInt(outputStream, handshakePacket.size)
        outputStream.write(handshakePacket)
        outputStream.flush()
    }

    private fun sendStatusRequest(socket: Socket) {
        val statusRequestPacket = PacketBuilder()
            .writeVarInt(0x00)
            .toByteArray()

        val outputStream = socket.getOutputStream()
        writeVarInt(outputStream, statusRequestPacket.size)
        outputStream.write(statusRequestPacket)
        outputStream.flush()
    }

    private fun receiveStatusResponse(socket: Socket): String {
        val inputStream = socket.getInputStream()
        val length = readVarInt(inputStream)
        val packetId = readVarInt(inputStream)

        if (packetId != 0x00) {
            throw IOException("Invalid packet ID: $packetId")
        }

        val jsonResponseLength = readVarInt(inputStream)
        val jsonResponseBytes = ByteArray(jsonResponseLength)
        var read = 0
        while (read < jsonResponseLength) {
            val count = inputStream.read(jsonResponseBytes, read, jsonResponseLength - read)
            if (count == -1) throw EOFException("Unexpected end of stream")
            read += count
        }

        return String(jsonResponseBytes, StandardCharsets.UTF_8)
    }

    private fun writeVarInt(outputStream: OutputStream, value: Int) {
        var remainingValue = value
        while (true) {
            if ((remainingValue and 0x7F) == remainingValue) {
                outputStream.write(remainingValue)
                break
            }
            outputStream.write((remainingValue and 0x7F) or 0x80)
            remainingValue = remainingValue ushr 7
        }
    }

    private fun readVarInt(inputStream: InputStream): Int {
        var value = 0
        var position = 0
        var byte: Int
        do {
            byte = inputStream.read()
            if (byte == -1) throw EOFException("Unexpected end of stream")
            value = value or ((byte and 0x7F) shl position)
            position += 7
        } while (byte and 0x80 != 0)
        return value
    }
}