package com.kvxd.mcserverinfo

import kotlinx.serialization.Serializable

@Serializable
data class ServerStatusResponse(
    val version: Version,
    val description: String,
    val players: Players,
) {
    @Serializable
    data class Version(val name: String, val protocol: Int)

    @Serializable
    data class Players(val max: Int, val online: Int)
}