package com.kvxd.mcserverinfo

data class MCServerQueryConfiguration(
    var address: String = "localhost", var port: Int = 25565, var timeout: Long = 5000,
    var encryptionCheck: EncryptionCheck = EncryptionCheck(),
) {
    
    data class EncryptionCheck(
        var enabled: Boolean = false,
        var username: String? = null,
    ) {
        
        fun enable() {
            enabled = true
        }
    }
    
    fun encryption(encryptionCheck: EncryptionCheck.() -> Unit): EncryptionCheck {
        this.encryptionCheck = EncryptionCheck().apply(encryptionCheck)
        return this.encryptionCheck
    }
    
}