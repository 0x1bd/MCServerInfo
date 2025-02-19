import com.kvxd.mcserverinfo.MCServerQuery
import com.kvxd.mcserverinfo.MCServerQueryResponse
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimpleTest {

    @Test
    fun test() = runBlocking {
        val t = measureTimeMillis {
            val expected = MCServerQueryResponse(
                version = MCServerQueryResponse.Version("1.21.4", 769),
                description = Component.text("A Minecraft Server"),
                players = MCServerQueryResponse.Players(max = 20, online = 0)
            )

            val serverAddress = "localhost"
            val query = MCServerQuery(serverAddress)

            assertTrue { query.isReachable() }

            val response = query.query()

            assertTrue { response == expected }
        }
        println("Local Server query: $t ms")
    }

    @Test
    fun testHypixel() = runBlocking {
        val t = measureTimeMillis {
            val serverAddress = "play.hypixel.net"
            val query = MCServerQuery(serverAddress)

            assertTrue(query.isReachable(), "Not reachable")

            assertNotNull(query.query())
        }
        println("Hypixel Server query: $t ms")
    }
}
