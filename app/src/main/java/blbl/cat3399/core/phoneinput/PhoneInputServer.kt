package blbl.cat3399.core.phoneinput

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import blbl.cat3399.core.log.AppLog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.concurrent.thread
import org.json.JSONException
import org.json.JSONObject

internal class PhoneInputServer(
    private val dispatchToMainThread: (() -> Unit) -> Unit = { action ->
        Handler(Looper.getMainLooper()).post { action() }
    },
) : Closeable {
    enum class Action { FILL, SEARCH }

    fun interface Listener {
        fun onInput(text: String, action: Action)
    }

    @Volatile
    private var listener: Listener? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    val isRunning: Boolean
        get() = serverSocket?.let { it.isBound && !it.isClosed } == true

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun start(): Boolean {
        if (isRunning) return true
        synchronized(this) {
            if (isRunning) return true
            return try {
                val socket = ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))
                serverSocket = socket
                acceptThread =
                    thread(name = "blbl-phone-input-accept", isDaemon = true) {
                        while (!socket.isClosed) {
                            val client =
                                try {
                                    socket.accept()
                                } catch (e: SocketException) {
                                    if (!socket.isClosed) AppLog.w(TAG, "accept failed", e)
                                    break
                                }
                            thread(name = "blbl-phone-input-worker", isDaemon = true) {
                                try {
                                    client.use(::handleClient)
                                } catch (e: IOException) {
                                    AppLog.w(TAG, "handle client failed", e)
                                }
                            }
                        }
                    }
                AppLog.i(TAG, "started port=$PORT")
                true
            } catch (e: IOException) {
                AppLog.w(TAG, "start failed", e)
                serverSocket = null
                acceptThread = null
                false
            }
        }
    }

    fun stop() {
        synchronized(this) {
            val socket = serverSocket ?: return
            serverSocket = null
            acceptThread = null
            try {
                socket.close()
            } catch (e: IOException) {
                AppLog.w(TAG, "stop failed", e)
            }
            AppLog.i(TAG, "stopped")
        }
    }

    override fun close() = stop()

    companion object {
        const val PORT = 9529
        private const val SOCKET_TIMEOUT_MS = 15_000
        private const val MAX_BODY_BYTES = 64 * 1024
        private const val TAG = "PhoneInput"

        fun getLanIp(): String? {
            val interfaces =
                try {
                    NetworkInterface.getNetworkInterfaces()
                } catch (e: SocketException) {
                    AppLog.w(TAG, "list network interfaces failed", e)
                    return null
                } ?: return null

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val usable =
                    try {
                        networkInterface.isUp && !networkInterface.isLoopback && !networkInterface.isVirtual
                    } catch (e: SocketException) {
                        AppLog.w(TAG, "inspect network interface failed", e)
                        false
                    }
                if (!usable) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
            return null
        }

        fun buildUrl(): String? {
            val ip = getLanIp() ?: return null
            return "http://$ip:$PORT/"
        }

        fun generateQrBitmap(url: String, size: Int = 512): Bitmap? {
            return try {
                val hints =
                    mapOf(
                        EncodeHintType.CHARACTER_SET to "UTF-8",
                        EncodeHintType.MARGIN to 1,
                    )
                val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size, hints)
                val pixels = IntArray(size * size)
                for (y in 0 until size) {
                    val rowOffset = y * size
                    for (x in 0 until size) {
                        pixels[rowOffset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                    }
                }
                Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                    setPixels(pixels, 0, size, 0, 0, size, size)
                }
            } catch (e: WriterException) {
                AppLog.w(TAG, "generate qr failed", e)
                null
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val input = socket.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
        val requestLine = input.readLine()?.trim().orEmpty()
        val parts = requestLine.split(' ', limit = 3)
        if (parts.size < 2) return

        val method = parts[0].uppercase(Locale.US)
        val path = parts[1].substringBefore('?')
        var contentLength = 0L
        while (true) {
            val line = input.readLine() ?: return
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0 && line.substring(0, separator).equals("content-length", ignoreCase = true)) {
                contentLength = line.substring(separator + 1).trim().toLongOrNull() ?: 0L
            }
        }

        if (method == "GET" && (path == "/" || path == "/index.html")) {
            respond(socket, 200, "OK", "text/html; charset=utf-8", inputPage())
        } else if (method == "GET" && path == "/status") {
            respond(socket, 200, "OK", "application/json; charset=utf-8", """{"running":true,"port":$PORT}""")
        } else if (method == "POST" && path == "/input") {
            handleInput(readBody(input, contentLength))
            respond(socket, 200, "OK", "application/json; charset=utf-8", """{"ok":true}""")
        } else {
            respond(socket, 404, "Not Found", "text/plain; charset=utf-8", "path=$path")
        }
    }

    private fun readBody(input: java.io.BufferedReader, contentLength: Long): String {
        if (contentLength <= 0L) return ""
        val buffer = CharArray(contentLength.toInt().coerceAtMost(MAX_BODY_BYTES))
        var length = 0
        while (length < buffer.size) {
            val count = input.read(buffer, length, buffer.size - length)
            if (count < 0) break
            length += count
        }
        val bytes = ByteArray(length) { index -> buffer[index].code.toByte() }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun handleInput(body: String) {
        val input =
            try {
                JSONObject(body)
            } catch (e: JSONException) {
                AppLog.w(TAG, "invalid input JSON", e)
                return
            }
        val text = input.optString("text").takeIf { it.isNotBlank() } ?: return
        val action = if (input.optString("action").equals("search", ignoreCase = true)) Action.SEARCH else Action.FILL
        dispatchToMainThread { listener?.onInput(text, action) }
    }

    private fun respond(socket: Socket, code: Int, reason: String, contentType: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val output = BufferedOutputStream(socket.getOutputStream())
        output.write("HTTP/1.1 $code $reason\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("Content-Type: $contentType\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("Content-Length: ${bytes.size}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }

    private fun inputPage(): String =
        """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>搜索 · 远程输入</title>
          <style>
            body { margin: 0; padding: 24px 16px; background: #1a1a1a; color: #fff; font: 16px sans-serif; }
            main { max-width: 520px; margin: 32px auto; }
            textarea { box-sizing: border-box; width: 100%; min-height: 120px; padding: 14px; border: 2px solid #4caf50; border-radius: 12px; background: #2a2a2a; color: #fff; font: inherit; }
            .buttons { display: flex; gap: 12px; margin-top: 16px; }
            button { flex: 1; padding: 14px 8px; border: 0; border-radius: 12px; font-size: 17px; font-weight: bold; }
            #status { min-height: 24px; margin-top: 16px; color: #4caf50; }
          </style>
        </head>
        <body>
          <main>
            <h1>搜索</h1>
            <textarea id="text" placeholder="输入搜索内容"></textarea>
            <div class="buttons">
              <button onclick="send('fill')">投送到搜索框</button>
              <button onclick="send('search')">直接搜索</button>
            </div>
            <div id="status"></div>
          </main>
          <script>
            async function send(action) {
              const text = document.getElementById('text').value;
              if (!text.trim()) return;
              const status = document.getElementById('status');
              status.textContent = '发送中...';
              try {
                const response = await fetch('/input', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ text, action })
                });
                const result = await response.json();
                status.textContent = result.ok ? '已发送' : '发送失败';
              } catch (_) {
                status.textContent = '网络错误，请确认手机与电视在同一局域网';
              }
            }
          </script>
        </body>
        </html>
        """.trimIndent()

}
