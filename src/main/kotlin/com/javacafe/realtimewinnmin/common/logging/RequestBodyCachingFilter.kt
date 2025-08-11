package com.javacafe.realtimewinnmin.common.logging

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.util.ContentCachingRequestWrapper

@Component
class RequestBodyCachingFilter : Filter {

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain
    ) {
        val httpRequest = request as HttpServletRequest

        // JSON 요청인 경우에만 ContentCachingRequestWrapper 적용
        val wrappedRequest =
            if (httpRequest.contentType?.contains("application/json", ignoreCase = true) == true) {
                ContentCachingRequestWrapper(httpRequest)
            } else {
                httpRequest
            }

        chain.doFilter(wrappedRequest, response)
    }
}
