package com.kvxd.mcserverinfo

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.geysermc.mcprotocollib.auth.SessionService
import org.geysermc.mcprotocollib.network.Session
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory
import org.geysermc.mcprotocollib.network.packet.Packet
import org.geysermc.mcprotocollib.protocol.MinecraftConstants
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol
import org.geysermc.mcprotocollib.protocol.data.UnexpectedEncryptionException
import org.geysermc.mcprotocollib.protocol.data.status.handler.ServerInfoHandler
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket
import java.net.InetSocketAddress
import java.net.Socket

class MCServerQuery private constructor(
    private val configuration: MCServerQueryConfiguration
) {

    companion object {

        fun create(configuration: MCServerQueryConfiguration.() -> Unit): MCServerQuery {
            return MCServerQuery(MCServerQueryConfiguration().apply(configuration))
        }
    }

    fun isReachable(): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(configuration.address, configuration.port),
                    configuration.timeout.toInt()
                )
                true
            }
        } catch (e: Exception) {
            false
        }

    suspend fun status(): MCServerStatus? = withTimeout(configuration.timeout) {
        val sessionService = SessionService()
        val protocol = MinecraftProtocol()

        val client = ClientNetworkSessionFactory.factory()
            .setRemoteSocketAddress(InetSocketAddress(configuration.address, configuration.port))
            .setProtocol(protocol)
            .create()

        client.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService)

        val status = CompletableDeferred<MCServerStatus?>()

        client.setFlag(
            MinecraftConstants.SERVER_INFO_HANDLER_KEY, ServerInfoHandler { session, info ->
                status.complete(
                    MCServerStatus(
                        MCServerStatus.Version(
                            info.versionInfo!!.versionName,
                            info.versionInfo!!.protocolVersion,
                        ), MCServerStatus.Players(
                            info.playerInfo!!.maxPlayers, info.playerInfo!!.onlinePlayers
                        ), info.description, info.iconPng, info.isEnforcesSecureChat
                    )
                )
            })

        client.connect()

        status.await()
    }

    suspend fun isEncrypted(): Boolean = withTimeout(configuration.timeout) {
        require(configuration.encryptionCheck.enabled) { "Encryption checking is disabled" }

        val protocol = MinecraftProtocol(configuration.encryptionCheck.username!!)

        val encrypted = CompletableDeferred<Boolean>()

        val client = ClientNetworkSessionFactory.factory()
            .setRemoteSocketAddress(InetSocketAddress(configuration.address, configuration.port))
            .setProtocol(protocol)
            .create()

        client.addListener(object : SessionAdapter() {
            override fun disconnected(event: DisconnectedEvent) {
                if (event.cause is UnexpectedEncryptionException) {
                    encrypted.complete(true)
                }
            }

            override fun packetReceived(session: Session, packet: Packet) {
                if (packet is ClientboundLoginFinishedPacket)
                    encrypted.complete(false)
            }
        })

        client.connect()

        encrypted.await().also {
            client.disconnect("Disconnected")
        }
    }

}