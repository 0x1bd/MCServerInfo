import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import com.kvxd.mcserverinfo.MinecraftServerPing
import com.kvxd.mcserverinfo.ServerStatusResponse
import kotlinx.coroutines.GlobalScope
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue

class SimpleTest {

    @Test
    fun test() {
        val x = AtomicInteger(0)

        val threads = List(10000) {
            thread {
                val expected = ServerStatusResponse(
                    version = ServerStatusResponse.Version("1.21.4", 769),
                    description = JsonPrimitive("A Minecraft Server"),
                    players = ServerStatusResponse.Players(max = 20, online = 0)
                )

                val serverAddress = "localhost"
                val minecraftServerPing = MinecraftServerPing(serverAddress, gson = Gson())

                // Ping the server and get the response
                val response = minecraftServerPing.ping()

                // Assert that the response matches the expected value
                assertTrue { response == expected }

                x.incrementAndGet()
            }
        }

        threads.forEach { it.join() }
        
        println(x.get())
    }
}