import com.kvxd.mcserverinfo.MCServerQuery
import com.kvxd.mcserverinfo.MCServerQueryConfiguration
import com.kvxd.mcserverinfo.MCServerStatus
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.system.measureTimeMillis
import kotlin.test.*

class MCServerQueryTest {

    private lateinit var localQuery: MCServerQuery
    private lateinit var hypixelQuery: MCServerQuery

    private val testEncryptionCheck =
        MCServerQueryConfiguration.EncryptionCheck(enabled = true, username = "Bot_" + Random.nextInt(0..100))

    @BeforeTest
    fun setUp() {
        // Initialize queries for local and Hypixel servers
        localQuery = MCServerQuery.create {
            address = "localhost"
            encryption(testEncryptionCheck)
        }

        hypixelQuery = MCServerQuery.create {
            address = "play.hypixel.net"
            encryption(testEncryptionCheck)
        }
    }

    @Test
    fun testLocalServerStatus() = runBlocking {
        val timeMS = measureTimeMillis {
            val expected = MCServerStatus(
                version = MCServerStatus.Version("1.21.4", 769),
                description = Component.text("A Minecraft Server"),
                players = MCServerStatus.Players(max = 20, online = 0)
            )

            val status = localQuery.status()
            assertEquals(expected, status, "Local server status did not match expected value")
        }
        println("Local Server status query took ${timeMS}ms")
    }

    @Test
    fun testLocalServerEncryption() = runBlocking {
        val timeMS = measureTimeMillis {
            val isEncrypted = localQuery.isEncrypted()
            assertFalse(isEncrypted, "Local server should not require encryption")
        }
        println("Local Server encryption check took ${timeMS}ms")
    }

    @Test
    fun testHypixelServerEncryption() = runBlocking {
        val timeMS = measureTimeMillis {
            val isEncrypted = hypixelQuery.isEncrypted()
            assertTrue(isEncrypted, "Hypixel server should require encryption")
        }
        println("Hypixel Server encryption check took ${timeMS}ms")
    }

    @Test
    fun testUnreachableServer() = runBlocking {
        val unreachableQuery = MCServerQuery.create {
            address = "nonexistent.server"
            encryption(testEncryptionCheck)
        }

        val timeMS = measureTimeMillis {
            assertFailsWith<Exception>("Querying an unreachable server should throw an exception") {
                unreachableQuery.status()
            }
        }
        println("Unreachable Server query took ${timeMS}ms")
    }

    @Test
    fun testLocalServerReachable() {
        val timeMS = measureTimeMillis {
            val isReachable = localQuery.isReachable()
            assertTrue(isReachable, "Local server should be reachable")
        }
        println("Local Server reachability check took ${timeMS}ms")
    }

    @Test
    fun testHypixelServerReachable() {
        val timeMS = measureTimeMillis {
            val isReachable = hypixelQuery.isReachable()
            assertTrue(isReachable, "Hypixel server should be reachable")
        }
        println("Hypixel Server reachability check took ${timeMS}ms")
    }

    @Test
    fun testUnreachableServerReachable() {
        val unreachableQuery = MCServerQuery.create {
            address = "nonexistent.server"
            encryption(testEncryptionCheck)
        }

        val timeMS = measureTimeMillis {
            val isReachable = unreachableQuery.isReachable()
            assertFalse(isReachable, "Unreachable server should not be reachable")
        }
        println("Unreachable Server reachability check took ${timeMS}ms")
    }
}