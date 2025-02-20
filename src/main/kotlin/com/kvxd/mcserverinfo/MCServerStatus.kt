package com.kvxd.mcserverinfo

import net.kyori.adventure.text.Component

data class MCServerStatus(
    var version: Version = Version("", -1),
    var players: Players = Players(0, 0),
    var description: Component = Component.text(""),
    var favicon: ByteArray? = null,
    var enforcesSecureChat: Boolean = false
) {
    data class Version(val name: String, val protocol: Int)
    data class Players(val max: Int, val online: Int)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MCServerStatus

        if (version != other.version) return false
        if (description != other.description) return false
        if (players != other.players) return false
        if (favicon != null) {
            if (other.favicon == null) return false
            if (!favicon.contentEquals(other.favicon)) return false
        } else if (other.favicon != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + players.hashCode()
        result = 31 * result + (favicon?.contentHashCode() ?: 0)
        return result
    }
}