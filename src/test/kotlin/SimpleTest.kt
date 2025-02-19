import com.kvxd.mcserverinfo.MCServerQuery
import com.kvxd.mcserverinfo.MCServerQueryResponse
import com.kvxd.mcserverinfo.OnlineMode
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.system.measureTimeMillis
import kotlin.test.*

class SimpleTest {

    @Test
    fun local() = runBlocking {
        val t = measureTimeMillis {
            
            val expected = MCServerQueryResponse(
                version = MCServerQueryResponse.Version("1.21.4", 769),
                description = Component.text("A Minecraft Server"),
                players = MCServerQueryResponse.Players(max = 20, online = 0)
            )

            val query = MCServerQuery.create {
                address = "localhost"
                
                encryption {
                    enable()
                    username = "CoolGuy69"
                }
            }

            val response = query.query()

            assertTrue(query.isReachable(), "Local server not reachable. Forgot to start it?")

            assertEquals(query.isEncrypted(response), OnlineMode.OFFLINE)
            assertEquals(query.isEncrypted(), OnlineMode.OFFLINE)
            
            repeat(50) {
                MCServerQuery.create {
                    address = "localhost"
                    
                    encryption {
                        enable()
                        username = "CoolGuy_" + Random.nextInt(0..100)
                    }
                }.isEncrypted()
            }

            assertTrue { response == expected }
        }
        println("Local Server query: $t ms")
    }


    @Test
    fun hypixel() = runBlocking {
        val t = measureTimeMillis {
            val query = MCServerQuery.create {
                address = "localhost"
                
                encryption {
                    enable()
                    username = "CoolGuy69"
                }
            }

            val response = query.query()

            assertTrue(query.isReachable(), "Hypixel server not reachable. Are you/the server online?")

            assertEquals(query.isEncrypted(response), OnlineMode.ONLINE)
            assertEquals(query.isEncrypted(), OnlineMode.ONLINE)

        }
        println("Hypixel Server query: $t ms")
    }

}
