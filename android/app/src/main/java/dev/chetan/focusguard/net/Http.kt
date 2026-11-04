package dev.chetan.focusguard.net

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val code: Int, message: String) : Exception(message)

/** Minimal blocking HTTP helpers (call from Dispatchers.IO). No third-party HTTP library needed. */
object Http {
    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 60_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 8_000
        c.readTimeout = timeoutMs
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Accept", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toByteArray()) }
        return read(c)
    }

    fun postMultipartFile(
        url: String, field: String, filename: String, mime: String, bytes: ByteArray,
        headers: Map<String, String> = emptyMap(), timeoutMs: Int = 90_000,
    ): String {
        val boundary = "----focusguard" + System.nanoTime()
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 8_000
        c.readTimeout = timeoutMs
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        c.setRequestProperty("Accept", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        val out = ByteArrayOutputStream()
        out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"$filename\"\r\nContent-Type: $mime\r\n\r\n".toByteArray())
        out.write(bytes)
        out.write("\r\n--$boundary--\r\n".toByteArray())
        c.outputStream.use { it.write(out.toByteArray()) }
        return read(c)
    }

    fun get(url: String, timeoutMs: Int = 8_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        return read(c)
    }

    private fun read(c: HttpURLConnection): String {
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpException(code, "HTTP $code: ${text.take(300)}")
            return text
        } finally {
            c.disconnect()
        }
    }
}
