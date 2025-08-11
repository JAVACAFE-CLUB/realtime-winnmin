package com.javacafe.realtimewinnmin.common.exception

import com.javacafe.realtimewinnmin.common.dto.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = KotlinLogging.logger { }


    @ExceptionHandler(GlobalException::class)
    protected fun handleGlobalException(e: GlobalException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error(e) { "[GlobalException] e : ${e.message} " }
        val response = ApiResponse.error(
            message = e.message ?: "Global Exception",
        )
        return ResponseEntity(response, e.exceptionCode.httpStatus)
    }
}
