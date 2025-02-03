package com.kvxd.mcserverinfo

import com.google.gson.JsonElement

data class ServerStatusResponse(
    val version: Version,
    val description: JsonElement,
    val players: Players,
    val favicon: String? = null,
) {

    data class Version(val name: String, val protocol: Int)

    data class Players(val max: Int, val online: Int, val sample: List<Player>? = null)

    data class Player(val name: String, val id: String)
}