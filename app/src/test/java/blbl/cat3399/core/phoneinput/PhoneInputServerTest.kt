package blbl.cat3399.core.phoneinput

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneInputServerTest {
    @Test
    fun servesPhoneInputPageAtRootAndIndexPaths() {
        val server = PhoneInputServer(dispatchToMainThread = { it() })
        try {
            assertTrue(server.start())

            for (path in listOf("/", "/index.html")) {
                val response = request(path)

                assertEquals(200, response.status)
                assertTrue(response.contentType.startsWith("text/html; charset=utf-8"))
                assertTrue(response.body.contains("投送到搜索框"))
                assertTrue(response.body.contains("直接搜索"))
                assertTrue(response.body.contains("已发送"))
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun reportsStatusAndReturnsNotFoundForUnsupportedRoutes() {
        val server = PhoneInputServer(dispatchToMainThread = { it() })
        try {
            assertTrue(server.start())

            val status = request("/status")
            assertEquals(200, status.status)
            assertEquals("application/json; charset=utf-8", status.contentType)
            assertEquals("""{"running":true,"port":9529}""", status.body)

            val missing = request("/missing")
            assertEquals(404, missing.status)
            assertEquals("path=/missing", missing.body)
        } finally {
            server.stop()
        }
    }

    @Test
    fun deliversUtf8FillAndSearchSubmissionsToListener() {
        val server = PhoneInputServer(dispatchToMainThread = { it() })
        val received = LinkedBlockingQueue<Input>()
        val receivedBoth = CountDownLatch(2)
        server.setListener(
            PhoneInputServer.Listener { text, action ->
                received.add(Input(text, action))
                receivedBoth.countDown()
            },
        )
        try {
            assertTrue(server.start())

            val fill = postInput("""{"text":"中文搜索","action":"fill"}""")
            assertEquals(200, fill.status)
            assertEquals("""{"ok":true}""", fill.body)

            val search = postInput("""{"text":"日本語 search","action":"search"}""")
            assertEquals(200, search.status)
            assertEquals("""{"ok":true}""", search.body)

            assertTrue(receivedBoth.await(5, TimeUnit.SECONDS))
            assertEquals(
                listOf(
                    Input("中文搜索", PhoneInputServer.Action.FILL),
                    Input("日本語 search", PhoneInputServer.Action.SEARCH),
                ),
                listOf(received.poll(), received.poll()),
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun stoppingServerReleasesFixedPort() {
        val server = PhoneInputServer(dispatchToMainThread = { it() })
        assertTrue(server.start())
        assertTrue(server.isRunning)

        server.stop()

        assertFalse(server.isRunning)
        assertThrows(IOException::class.java) {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", PhoneInputServer.PORT), 500) }
        }
    }

    private fun request(path: String): Response {
        val connection = URL("http://127.0.0.1:${PhoneInputServer.PORT}$path").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        return readResponse(connection)
    }

    private fun postInput(body: String): Response {
        val connection = URL("http://127.0.0.1:${PhoneInputServer.PORT}/input").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): Response {
        return try {
            val status = connection.responseCode
            val responseStream = if (status >= 400) connection.errorStream else connection.inputStream
            Response(
                status = status,
                contentType = connection.contentType.orEmpty(),
                body = responseStream.bufferedReader(Charsets.UTF_8).use { it.readText() },
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class Input(val text: String, val action: PhoneInputServer.Action)

    private data class Response(val status: Int, val contentType: String, val body: String)
}
