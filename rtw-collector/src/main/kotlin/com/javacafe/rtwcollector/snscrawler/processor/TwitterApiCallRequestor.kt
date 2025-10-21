package com.javacafe.rtwcollector.snscrawler.processor

import com.fasterxml.jackson.databind.ObjectMapper
import com.javacafe.rtwcollector.snscrawler.model.TwitterSearchResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Service
class TwitterApiCallRequestor(
    private val httpClient: HttpClient,
    private val objectMapper: ObjectMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    @Value("\${twitter.api-url:https://api.twitter.com/2/tweets/search/recent}")
    private lateinit var twitterApiUrl: String

    @Value("\${twitter.token:}")
    private lateinit var twitterBearerToken: String

    private val logger = KotlinLogging.logger { }

    suspend fun fetchTwitterContent(searchParam: String): TwitterSearchResponse = withContext(ioDispatcher) {
        val urlWithParams = "$twitterApiUrl?max_results=10&query=${URLEncoder.encode(searchParam, "UTF-8")}"

        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(urlWithParams))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer $twitterBearerToken")
                .header("Accept", "application/json")
                .GET()
                .build()

            val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .await()

            if (response.statusCode() !in 200..299) {
                throw IOException("HTTP ${response.statusCode()} for $twitterApiUrl")
            }

            val content = response.body()?.let { body ->
                objectMapper.readValue(body, TwitterSearchResponse::class.java)
            } ?: throw IOException("Empty response body for $twitterApiUrl")

            content
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch content from $twitterApiUrl" }
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
