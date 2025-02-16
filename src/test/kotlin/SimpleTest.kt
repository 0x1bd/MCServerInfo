import com.kvxd.mcserverinfo.MinecraftServerPing
import com.kvxd.mcserverinfo.ServerStatusResponse
import net.kyori.adventure.text.Component
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimpleTest {

    @Test
    fun test() {
        val expected = ServerStatusResponse(
            version = ServerStatusResponse.Version("1.21.4", 769),
            description = Component.text("A Minecraft Server"),
            players = ServerStatusResponse.Players(max = 20, online = 0)
        )

        val serverAddress = "localhost"
        val minecraftServerPing = MinecraftServerPing(serverAddress)

        assertTrue { minecraftServerPing.reachable }

        // Ping the server and get the response
        val response = minecraftServerPing.ping()

        // Assert that the response matches the expected value
        assertTrue { response == expected }
    }

    @Test
    fun testHypixel() {
        val serverAddress = "play.hypixel.net"
        val minecraftServerPing = MinecraftServerPing(serverAddress)

        assertTrue(minecraftServerPing.reachable, "Not reachable")

        assertNotNull(minecraftServerPing.ping())
    }
}