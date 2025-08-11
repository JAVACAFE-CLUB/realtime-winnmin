package com.javacafe.realtimewinnmin.common.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.util.ContentCachingRequestWrapper

@Aspect
@Component
class LoggingAspect {

    private val logger = KotlinLogging.logger {}

    @Around("execution(* com.javacafe.realtimewinnmin.presentation.controller.*.*(..))")
    fun logApiRequest(joinPoint: ProceedingJoinPoint): Any? {
        val signature = "${joinPoint.signature.declaringType.simpleName}.${joinPoint.signature.name}"
        val request = getCurrentRequest()

        logRequest(request, signature)

        return joinPoint.proceed()
    }

    private fun getCurrentRequest(): HttpServletRequest? =
        runCatching {
            (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        }.onFailure {
            logger.debug { "HttpServletRequest 파싱 실패: ${it.message}" }
        }.getOrNull()

    private fun logRequest(request: HttpServletRequest?, signature: String) {
        when (request) {
            null -> logger.info { "API Call - $signature" }
            else -> {
                val fullUrl = buildString {
                    append(request.requestURI)
                    request.queryString?.let { append("?$it") }
                }

                logger.info { "${request.method} $fullUrl - $signature" }

                logger.takeIf { it.isDebugEnabled() }?.apply {
                    logRequestParameters(request)
                    logRequestBody(request)
                }
            }
        }
    }

    private fun logRequestParameters(request: HttpServletRequest) {
        request.parameterMap
            .takeIf { it.isNotEmpty() }
            ?.let { params ->
                val paramString = params.entries.joinToString(", ") {
                    "${it.key}=${it.value.joinToString(",")}"
                }
                logger.debug { ">>> Request parameters : $paramString" }
            }
    }

    private fun logRequestBody(request: HttpServletRequest) {
        request.contentType
            ?.takeIf { it.contains("application/json", ignoreCase = true) }
            ?.let {
                val body = extractRequestBody(request)
                body.takeIf { it.isNotBlank() }
                    ?.let { logger.debug { ">>> Request body: $it" } }
            }
    }

    /**
     * 요청 바디 추출
     */
    private fun extractRequestBody(request: HttpServletRequest): String =
        runCatching {
            (request as? ContentCachingRequestWrapper)
                ?.contentAsByteArray
                ?.takeIf { it.isNotEmpty() }
                ?.let { String(it, Charsets.UTF_8) }
                ?: ""
        }.onFailure {
            logger.debug { "Could not read request body: ${it.message}" }
        }.getOrDefault("")
}