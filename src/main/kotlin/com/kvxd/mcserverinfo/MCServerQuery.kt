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
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.DataFormatException
import java.util.zip.Inflater

class MCServerQuery private constructor(
    private val configuration: MCServerQueryConfiguration,
    private val gson: Gson = createDefaultGson()
) {

    suspend fun isReachable(): Boolean = coroutineScope {
        try {
            withTimeoutOrNull(configuration.timeout.toLong()) {
                val channel = AsynchronousSocketChannel.open()
                channel.use { c ->
                    c.connect(InetSocketAddress(configuration.address, configuration.port))
                        .get(configuration.timeout.toLong(), TimeUnit.MILLISECONDS)
                    true
                }
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isEncrypted(query: MCServerQueryResponse? = null): OnlineMode = withTimeout(configuration.timeout) {
        val status = query ?: query()
        val protocolVersion = status.version.protocol // Local variable

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
        val packet = PacketBuilder()
            .writeVarInt(0x00)
            .writeVarInt(protocolVersion)
            .writeString(configuration.address)
            .writeShort(configuration.port)
            .writeVarInt(1)
            .toByteArray()

        sendPacket(channel, packet)
    }

    private fun sendStatusRequest(channel: AsynchronousSocketChannel) {
        val packet = PacketBuilder()
            .writeVarInt(0x00)
            .toByteArray()

        sendPacket(channel, packet)
    }
    
    private fun sendLoginHandshake(channel: AsynchronousSocketChannel, protocolVersion: Int) {
        val packet = PacketBuilder().apply {
            writeVarInt(0x00)
            writeVarInt(protocolVersion)
            writeString(configuration.address)
            writeShort(configuration.port)
            writeVarInt(2) // Next state: 2 for login
        }.toByteArray()

        sendPacket(channel, packet)
    }

    private fun sendLoginStart(channel: AsynchronousSocketChannel, protocolVersion: Int) {
        if (configuration.encryptionCheckUsername == null) throw IllegalStateException("Encryption check not supported if encryptionCheckUsername is null")

        val uuid = if (protocolVersion >= 761) {
            val name = "OfflinePlayer:${configuration.encryptionCheckUsername}"
            UUID.nameUUIDFromBytes(name.toByteArray(StandardCharsets.UTF_8))
        } else {
            UUID(0, 0) // For versions <761, UUID is not sent
        }

        val packet = PacketBuilder().apply {
            writeVarInt(0x00) // Login Start packet ID
            writeString(configuration.encryptionCheckUsername!!)

            if (protocolVersion >= 761) {
                writeLong(uuid.mostSignificantBits)
                writeLong(uuid.leastSignificantBits)
            }
        }.toByteArray()

        sendPacket(channel, packet)
    }

    private suspend fun checkForEncryptionRequest(channel: AsynchronousSocketChannel): OnlineMode {
        var compressionThreshold = -1

        return try {
            while (true) {
                val packetLength = readVarInt(channel)

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

                val packetId = packetBuffer.readVarInt()
                when (packetId) {
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

    private fun decompressZlib(data: ByteArray, expectedLength: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val result = ByteArray(expectedLength)
        val resultLength = inflater.inflate(result)
        inflater.end()
        if (resultLength != expectedLength) {
            throw IOException("Decompressed data length mismatch: expected $expectedLength, got $resultLength")
        }
        return result
    }
    
    private fun handleLoginPluginRequest(channel: AsynchronousSocketChannel, buffer: ByteBuffer) {
        val messageId = buffer.readVarInt()
        val channelName = buffer.readString()
        val data = ByteArray(buffer.remaining()).apply { buffer.get(this) }

        // Respond indicating no data understood
        val response = PacketBuilder()
            .writeVarInt(0x02) // Login Plugin Response ID
            .writeVarInt(messageId)
            .writeBoolean(false)
            .toByteArray()

        sendPacket(channel, response)
    }

    private fun handleCookieRequest(channel: AsynchronousSocketChannel, buffer: ByteBuffer) {
        val key = buffer.readString()

        // Respond with no payload
        val response = PacketBuilder()
            .writeVarInt(0x04) // Cookie Response ID
            .writeString(key)
            .writeBoolean(false) // No payload
            .toByteArray()

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
                channel.read(byteBuffer).get(configuration.timeout, TimeUnit.MILLISECONDS)
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

        fun create(configuration: MCServerQueryConfiguration.() -> Unit): MCServerQuery {
            val config = MCServerQueryConfiguration().apply(configuration)

            return MCServerQuery(config)
        }
    }
}