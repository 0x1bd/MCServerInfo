import com.kvxd.mcserverinfo.MinecraftServerPing
import com.kvxd.mcserverinfo.ServerStatusResponse
import kotlin.test.Test
import kotlin.test.assertTrue

class SimpleTest {

    @Test
    fun test() {
        val expected = ServerStatusResponse(
            version = ServerStatusResponse.Version("1.21.4", 769),
            description = "A Minecraft Server",
            players = ServerStatusResponse.Players(max = 20, online = 0)
        )

        val serverAddress = "localhost"
        val minecraftServerPing = MinecraftServerPing(serverAddress)

        val response = minecraftServerPing.ping()

        assertTrue { response == expected }
    }

}