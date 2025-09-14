package com.javacafe.rtwcollector.rsscrawler.processor

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Service
class HttpClientComponent(
    private val httpClient: HttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val logger = KotlinLogging.logger { }

    suspend fun fetchContent(url: String): String = withContext(ioDispatcher) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/rss+xml, application/xml, text/xml")
                .header("Accept-Charset", "UTF-8, EUC-KR")
                .header("User-Agent", "RSS-Crawler/1.0")
                .GET()
                .build()

            val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(Charset.forName("EUC-KR")))
                .await()

            if (response.statusCode() !in 200..299) {
                throw IOException("HTTP ${response.statusCode()} for $url")
            }

            val content = response.body() ?: throw IOException("Empty response body for $url")

            content
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch content from $url" }
            throw e
        }
    }
}

suspend fun <T> CompletableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    whenComplete { result, exception ->
        when {
            exception != null -> cont.resumeWithException(exception)
            else -> cont.resume(result)
        }
    }

    cont.invokeOnCancellation {
        cancel(true)
    }
}
