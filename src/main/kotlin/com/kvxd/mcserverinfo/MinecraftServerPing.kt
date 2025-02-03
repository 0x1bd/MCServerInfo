package com.kvxd.mcserverinfo

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

class MinecraftServerPing(
    private val serverAddress: String,
    private val serverPort: Int = 25565,
    private val timeout: Int = 5000,
    private val gson: Gson
) {
    private val protocolVersion: Int = -1 // Latest

    suspend fun ping(): ServerStatusResponse = withContext(Dispatchers.IO) {
        val socketChannel = AsynchronousSocketChannel.open()
        val connectFuture = CompletableFuture<Void>()

        socketChannel.connect(InetSocketAddress(serverAddress, serverPort), null, object : CompletionHandler<Void, Void> {
            override fun completed(result: Void?, attachment: Void?) {
                connectFuture.complete(null)
            }

            override fun failed(exc: Throwable, attachment: Void?) {
                connectFuture.completeExceptionally(exc)
            }
        })

        connectFuture.get() // Wait for connection

        try {
            sendHandshake(socketChannel)
            sendStatusRequest(socketChannel)
            val response = receiveStatusResponse(socketChannel)
            return@withContext gson.fromJson(response, ServerStatusResponse::class.java)
        } finally {
            socketChannel.close()
        }
    }

    private suspend fun sendHandshake(socketChannel: AsynchronousSocketChannel) {
        val handshakePacket = PacketBuilder()
            .writeVarInt(0x00)
            .writeVarInt(protocolVersion)
            .writeString(serverAddress)
            .writeShort(serverPort)
            .writeVarInt(1) // 1 = Status

        writeVarInt(socketChannel, handshakePacket.toByteArray().size)
        socketChannel.write(ByteBuffer.wrap(handshakePacket.toByteArray())).get()
    }

    private suspend fun sendStatusRequest(socketChannel: AsynchronousSocketChannel) {
        val statusRequestPacket = PacketBuilder()
            .writeVarInt(0x00)

        writeVarInt(socketChannel, statusRequestPacket.toByteArray().size)
        socketChannel.write(ByteBuffer.wrap(statusRequestPacket.toByteArray())).get()
    }

    private suspend fun receiveStatusResponse(socketChannel: AsynchronousSocketChannel): String {
        val length = readVarInt(socketChannel)
        val packetId = readVarInt(socketChannel)

        if (packetId != 0x00) {
            throw IOException("Invalid packet ID: $packetId")
        }

        val jsonResponseLength = readVarInt(socketChannel)
        val jsonResponseBuffer = ByteBuffer.allocate(jsonResponseLength)
        socketChannel.read(jsonResponseBuffer).get()
        jsonResponseBuffer.flip()

        return StandardCharsets.UTF_8.decode(jsonResponseBuffer).toString()
    }

    private suspend fun writeVarInt(socketChannel: AsynchronousSocketChannel, value: Int) {
        val buffer = ByteBuffer.allocate(5) // Max size for a VarInt
        var remainingValue = value
        while (true) {
            if ((remainingValue and 0x7F) == remainingValue) {
                buffer.put(remainingValue.toByte())
                break
            }
            buffer.put((remainingValue and 0x7F or 0x80).toByte())
            remainingValue = remainingValue ushr 7
        }
        buffer.flip()
        socketChannel.write(buffer).get()
    }

    private suspend fun readVarInt(socketChannel: AsynchronousSocketChannel): Int {
        val buffer = ByteBuffer.allocate(1)
        var value = 0
        var position = 0
        var byte: Int
        do {
            socketChannel.read(buffer).get()
            buffer.flip()
            byte = buffer.get().toInt() and 0xFF
            value = value or ((byte and 0x7F) shl position)
            position += 7
            buffer.clear()
        } while (byte and 0x80 != 0)
        return value
    }
}