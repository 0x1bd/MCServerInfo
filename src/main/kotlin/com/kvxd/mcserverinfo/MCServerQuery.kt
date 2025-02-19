package com.kvxd.mcserverinfo

import com.google.gson.Gson
import kotlinx.coroutines.*
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class MCServerQuery(
    private val serverAddress: String,
    private val serverPort: Int = 25565,
    private val timeout: Int = 5000,
    private val gson: Gson = createDefaultGson()
) {
    private val protocolVersion: Int = -1 // Latest

    suspend fun isReachable(): Boolean = coroutineScope {
        try {
            withTimeoutOrNull(timeout.toLong()) {
                val channel = AsynchronousSocketChannel.open()
                channel.use { c ->
                    c.connect(InetSocketAddress(serverAddress, serverPort)).get(timeout.toLong(), TimeUnit.MILLISECONDS)
                    true
                }
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun query(): MCServerQueryResponse = withTimeout(timeout.toLong()) {
        val channel = AsynchronousSocketChannel.open()
        try {
            channel.connect(InetSocketAddress(serverAddress, serverPort)).get(timeout.toLong(), TimeUnit.MILLISECONDS)

            sendHandshake(channel)
            sendStatusRequest(channel)

            parseResponse(channel)
        } finally {
            channel.close()
        }
    }

    private suspend fun sendHandshake(channel: AsynchronousSocketChannel) {
        val packet = PacketBuilder()
            .writeVarInt(0x00)
            .writeVarInt(protocolVersion)
            .writeString(serverAddress)
            .writeShort(serverPort)
            .writeVarInt(1)
            .toByteArray()

        sendPacket(channel, packet)
    }

    private suspend fun sendStatusRequest(channel: AsynchronousSocketChannel) {
        val packet = PacketBuilder()
            .writeVarInt(0x00)
            .toByteArray()

        sendPacket(channel, packet)
    }

    private fun sendPacket(channel: AsynchronousSocketChannel, packet: ByteArray) {
        val lengthBuffer = encodeVarInt(packet.size)
        val buffer = ByteBuffer.allocate(lengthBuffer.remaining() + packet.size).apply {
            put(lengthBuffer)
            put(packet)
            flip()
        }

        while (buffer.hasRemaining()) {
            channel.write(buffer).get(timeout.toLong(), TimeUnit.MILLISECONDS)
        }
    }

    private suspend fun AsynchronousSocketChannel.readFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            val bytesRead = read(buffer).get(timeout.toLong(), TimeUnit.MILLISECONDS)
            if (bytesRead == -1) throw EOFException("Unexpected end of stream")
        }
    }

    private suspend fun parseResponse(channel: AsynchronousSocketChannel): MCServerQueryResponse {
        val packetLength = readVarInt(channel)

        val packetBuffer = ByteBuffer.allocate(packetLength).apply {
            channel.readFully(this)
            flip()
        }

        val packetId = packetBuffer.readVarInt()
        if (packetId != 0x00) throw IOException("Invalid packet ID: $packetId")

        val jsonLength = packetBuffer.readVarInt()
        val jsonBytes = ByteArray(jsonLength).apply {
            packetBuffer.get(this)
        }

        return gson.fromJson(String(jsonBytes, StandardCharsets.UTF_8), MCServerQueryResponse::class.java)
    }

    private fun ByteBuffer.readVarInt(): Int {
        var value = 0
        var position = 0
        var currentByte: Int

        do {
            currentByte = get().toInt() and 0xFF
            value = value or ((currentByte and 0x7F) shl position)
            position += 7
        } while ((currentByte and 0x80) != 0)

        return value
    }

    private suspend fun readVarInt(channel: AsynchronousSocketChannel): Int {
        var value = 0
        var position = 0
        var currentByte: Int

        do {
            val byteBuffer = ByteBuffer.allocate(1)
            withContext(Dispatchers.IO) {
                channel.read(byteBuffer).get(timeout.toLong(), TimeUnit.MILLISECONDS)
            }
            byteBuffer.flip()
            currentByte = byteBuffer.get().toInt() and 0xFF
            value = value or ((currentByte and 0x7F) shl position)
            position += 7
        } while ((currentByte and 0x80) != 0)

        return value
    }

    private fun encodeVarInt(value: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(5)
        var remaining = value

        do {
            var temp = (remaining and 0x7F).toByte()
            remaining = remaining ushr 7
            if (remaining != 0) {
                temp = (temp.toInt() or 0x80).toByte()
            }
            buffer.put(temp)
        } while (remaining != 0)

        buffer.flip()
        return buffer
    }

    companion object {
        private fun createDefaultGson(): Gson = GsonComponentSerializer.gson().serializer()
    }
}