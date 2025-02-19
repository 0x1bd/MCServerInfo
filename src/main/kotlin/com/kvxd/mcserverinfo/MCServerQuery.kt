package com.kvxd.mcserverinfo

import com.google.gson.Gson
import com.kvxd.mcserverinfo.protocol.*
import com.kvxd.mcserverinfo.util.NetworkUtils.decompressZlib
import com.kvxd.mcserverinfo.util.NetworkUtils.encodeVarInt
import com.kvxd.mcserverinfo.util.NetworkUtils.readVarInt
import com.kvxd.mcserverinfo.util.NetworkUtils.sendPacket
import kotlinx.coroutines.*
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.DataFormatException
import java.util.zip.Inflater

class MCServerQuery private constructor(
    private val configuration: MCServerQueryConfiguration,
    private val gson: Gson = createDefaultGson()
) {

    suspend fun isReachable(): Boolean = coroutineScope {
        try {
            withTimeoutOrNull(configuration.timeout) {
                val channel = AsynchronousSocketChannel.open()
                channel.use { c ->
                    c.connect(InetSocketAddress(configuration.address, configuration.port))
                        .get(configuration.timeout, TimeUnit.MILLISECONDS)
                    true
                }
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isEncrypted(query: MCServerQueryResponse? = null): OnlineMode = withTimeout(configuration.timeout) {
        val status = query ?: query()
        val protocolVersion = status.version.protocol

        AsynchronousSocketChannel.open().use { channel ->
            channel.connect(InetSocketAddress(configuration.address, configuration.port))
                .get(configuration.timeout, TimeUnit.MILLISECONDS)

            sendLoginHandshake(channel, protocolVersion)
            sendLoginStart(channel, protocolVersion)

            return@withTimeout checkForEncryptionRequest(channel)
        }
    }

    suspend fun query(): MCServerQueryResponse = withTimeout(configuration.timeout) {
        val channel = AsynchronousSocketChannel.open()
        channel.use { c ->
            c.connect(InetSocketAddress(configuration.address, configuration.port))
                .get(configuration.timeout, TimeUnit.MILLISECONDS)

            sendHandshake(c, -1)
            sendStatusRequest(c)

            parseResponse(c)
        }
    }

    private fun sendHandshake(channel: AsynchronousSocketChannel, protocolVersion: Int) {
        val packet = HandshakePacket(
            protocolVersion = protocolVersion,
            address = configuration.address,
            port = configuration.port,
            nextState = 1
        ).toByteArray()

        channel.sendPacket(packet, configuration.timeout)
    }

    private fun sendStatusRequest(channel: AsynchronousSocketChannel) {
        val packet = StatusRequestPacket().toByteArray()
        channel.sendPacket(packet, configuration.timeout)
    }

    private fun sendLoginHandshake(channel: AsynchronousSocketChannel, protocolVersion: Int) {
        val packet = HandshakePacket(
            protocolVersion = protocolVersion,
            address = configuration.address,
            port = configuration.port,
            nextState = 2
        ).toByteArray()

        channel.sendPacket(packet, configuration.timeout)
    }

    private fun sendLoginStart(channel: AsynchronousSocketChannel, protocolVersion: Int) {
        val packet = LoginStartPacket(
            username = configuration.encryptionCheckUsername!!,
            protocolVersion = protocolVersion
        ).toByteArray()

        channel.sendPacket(packet, configuration.timeout)
    }

    private suspend fun checkForEncryptionRequest(channel: AsynchronousSocketChannel): OnlineMode {
        var compressionThreshold = -1

        return try {
            while (true) {
                val packetLength = channel.readVarInt(configuration.timeout)

                val packetBuffer = if (compressionThreshold == -1) {
                    ByteBuffer.allocate(packetLength).apply {
                        channel.readFully(this)
                        flip()
                    }
                } else {
                    val compressedBuffer = ByteBuffer.allocate(packetLength).apply {
                        channel.readFully(this)
                        flip()
                    }

                    val dataLength = compressedBuffer.readVarInt()
                    if (dataLength == 0) {
                        compressedBuffer.slice()
                    } else {
                        val compressedData = ByteArray(compressedBuffer.remaining()).apply { compressedBuffer.get(this) }
                        try {
                            val decompressedData = decompressZlib(compressedData, dataLength)
                            ByteBuffer.wrap(decompressedData)
                        } catch (e: DataFormatException) {
                            throw IOException("Failed to decompress packet data", e)
                        }
                    }
                }

                when (val packetId = packetBuffer.readVarInt()) {
                    0x00 -> return handleDisconnect(packetBuffer)
                    0x01 -> return OnlineMode.ONLINE
                    0x02 -> return OnlineMode.OFFLINE
                    0x03 -> {
                        compressionThreshold = packetBuffer.readVarInt()
                        continue
                    }
                    0x04 -> handleLoginPluginRequest(channel, packetBuffer)
                    0x05 -> handleCookieRequest(channel, packetBuffer)
                    else -> throw IOException("Unexpected packet ID: $packetId")
                }
            }
            OnlineMode.UNKNOWN
        } catch (e: Exception) {
            OnlineMode.UNKNOWN
        }
    }


    private fun ByteBuffer.readString(): String {
        val length = readVarInt()
        val bytes = ByteArray(length).apply { get(this) }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun handleLoginPluginRequest(channel: AsynchronousSocketChannel, buffer: ByteBuffer) {
        val messageId = buffer.readVarInt()
        val channelName = buffer.readString()
        val data = ByteArray(buffer.remaining()).apply { buffer.get(this) }

        val response = LoginPluginResponsePacket(messageId).toByteArray()
        sendPacket(channel, response)
    }

    private fun handleCookieRequest(channel: AsynchronousSocketChannel, buffer: ByteBuffer) {
        val key = buffer.readString()

        val response = CookieResponsePacket(key).toByteArray()
        sendPacket(channel, response)
    }

    private fun handleDisconnect(buffer: ByteBuffer): OnlineMode {
        val reasonLength = buffer.readVarInt()
        val reasonBytes = ByteArray(reasonLength).apply { buffer.get(this) }
        val reason = String(reasonBytes, StandardCharsets.UTF_8)
        println(reason)
        return if (reason.contains("multiplayer.disconnect.unverified_username"))
            OnlineMode.OFFLINE
        else if (reason.contains("Double login"))
            OnlineMode.UNKNOWN
        else
            OnlineMode.ONLINE
    }

    private fun sendPacket(channel: AsynchronousSocketChannel, packet: ByteArray) {
        val lengthBuffer = encodeVarInt(packet.size)
        val buffer = ByteBuffer.allocate(lengthBuffer.remaining() + packet.size).apply {
            put(lengthBuffer)
            put(packet)
            flip()
        }

        while (buffer.hasRemaining()) {
            channel.write(buffer).get(configuration.timeout, TimeUnit.MILLISECONDS)
        }
    }

    private fun AsynchronousSocketChannel.readFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            val bytesRead = read(buffer).get(configuration.timeout, TimeUnit.MILLISECONDS)
            if (bytesRead == -1) throw EOFException("Unexpected end of stream")
        }
    }

    private suspend fun parseResponse(channel: AsynchronousSocketChannel): MCServerQueryResponse {
        val packetLength = channel.readVarInt(configuration.timeout)

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

    companion object {
        private fun createDefaultGson(): Gson = GsonComponentSerializer.gson().serializer()

        fun create(configuration: MCServerQueryConfiguration.() -> Unit): MCServerQuery {
            val config = MCServerQueryConfiguration().apply(configuration)

            return MCServerQuery(config)
        }
    }
}