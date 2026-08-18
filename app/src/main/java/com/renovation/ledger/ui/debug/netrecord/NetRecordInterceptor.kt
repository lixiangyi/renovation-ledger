package com.renovation.ledger.ui.debug.netrecord

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.EOFException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class NetRecordInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTimeMs = System.currentTimeMillis()
        val startNano = System.nanoTime()
        val response = chain.proceed(request)
        val durationMs = (System.nanoTime() - startNano) / 1_000_000

        runCatching {
            recordIfPlainText(request, response, startTimeMs, durationMs)
        }
        return response
    }

    private fun recordIfPlainText(
        request: Request,
        response: Response,
        startTimeMs: Long,
        durationMs: Long,
    ) {
        val responseBody = response.body ?: return
        if (bodyEncoded(response.headers)) return

        val source = responseBody.source()
        source.request(Long.MAX_VALUE)
        val buffer = source.buffer.clone()

        val contentType = responseBody.contentType()
        val charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
        if (!isPlaintext(buffer)) return
        if (buffer.size == 0L) return

        val bodyText = buffer.readString(charset)
        val requestBean = NetRecordBean.RequestBean(
            method = request.method,
            url = request.url.toString(),
            header = request.headers.toMultilineString(),
            postBody = readRequestBody(request),
            curl = buildCurl(request),
            startTimeMs = startTimeMs,
        )
        val responseBean = NetRecordBean.ResponseBean(
            body = bodyText,
            statusCode = response.code,
            bodySizeKb = String.format("%.2f", bodyText.toByteArray(charset).size / 1024f),
            durationMs = durationMs,
        )
        NetRecordStore.add(NetRecordBean(requestBean, responseBean))
    }

    private fun readRequestBody(request: Request): String {
        val body = request.body ?: return ""
        return runCatching {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }.getOrDefault("")
    }

    private fun buildCurl(request: Request): String {
        val builder = StringBuilder("curl -X ")
            .append(request.method)
            .append(" '")
            .append(request.url)
            .append("'")
        request.headers.names().forEach { name ->
            builder.append(" -H '")
                .append(name)
                .append(": ")
                .append(request.header(name).orEmpty())
                .append("'")
        }
        val body = readRequestBody(request)
        if (body.isNotEmpty()) {
            builder.append(" --data '").append(body).append("'")
        }
        return builder.toString()
    }

    private fun Headers.toMultilineString(): String =
        names().joinToString("\n") { name -> "$name: ${this[name].orEmpty()}" }

    private fun bodyEncoded(headers: Headers): Boolean {
        val encoding = headers["Content-Encoding"] ?: return false
        return !encoding.equals("identity", ignoreCase = true)
    }

    private fun isPlaintext(buffer: Buffer): Boolean {
        return try {
            val prefix = Buffer()
            val byteCount = if (buffer.size < 64) buffer.size else 64
            buffer.copyTo(prefix, 0, byteCount)
            for (i in 0 until 16) {
                if (prefix.exhausted()) break
                val codePoint = prefix.readUtf8CodePoint()
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false
                }
            }
            true
        } catch (_: EOFException) {
            false
        }
    }
}
