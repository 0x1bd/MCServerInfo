package com.kvxd.mcserverinfo.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit
import java.util.zip.DataFormatException
import java.util.zip.Inflater

object NetworkUtils {
    
    fun ByteBuffer.readVarInt(): Int {
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

    fun encodeVarInt(value: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(5)
        var remaining = value
        do {
            var temp = (remaining and 0x7F).toByte()
            remaining = remaining ushr 7
            if (remaining != 0) temp = (temp.toInt() or 0x80).toByte()
            buffer.put(temp)
        } while (remaining != 0)
        buffer.flip()
        return buffer
    }

    fun decompressZlib(data: ByteArray, expectedLength: Int): ByteArray {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val result = ByteArray(expectedLength)
            inflater.inflate(result)
            result
        } catch (e: DataFormatException) {
            throw IOException("Failed to decompress packet data", e)
        } finally {
            inflater.end()
        }
    }

    suspend fun AsynchronousSocketChannel.readVarInt(timeout: Long): Int {
        var value = 0
        var position = 0
        var currentByte: Int
        do {
            val byteBuffer = ByteBuffer.allocate(1)
            readFully(byteBuffer, timeout)
            byteBuffer.flip()
            currentByte = byteBuffer.get().toInt() and 0xFF
            value = value or ((currentByte and 0x7F) shl position)
            position += 7
        } while ((currentByte and 0x80) != 0)
        return value
    }

    suspend fun AsynchronousSocketChannel.readFully(buffer: ByteBuffer, timeout: Long) = withContext(Dispatchers.IO) {
        while (buffer.hasRemaining()) {
            val bytesRead = read(buffer).get(timeout, TimeUnit.MILLISECONDS)
            if (bytesRead == -1) throw EOFException("Unexpected end of stream")
        }
    }

    fun AsynchronousSocketChannel.sendPacket(packet: ByteArray, timeout: Long) {
        val lengthBuffer = encodeVarInt(packet.size)
        val buffer = ByteBuffer.allocate(lengthBuffer.remaining() + packet.size).apply {
            put(lengthBuffer)
            put(packet)
            flip()
        }
        while (buffer.hasRemaining()) {
            write(buffer).get(timeout, TimeUnit.MILLISECONDS)
        }
    }
}