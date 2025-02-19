package com.kvxd.mcserverinfo

data class MCServerQueryConfiguration(
    var address: String = "localhost", var port: Int = 25565, var timeout: Long = 5000,
    var encryptionCheckUsername: String? = null,
)