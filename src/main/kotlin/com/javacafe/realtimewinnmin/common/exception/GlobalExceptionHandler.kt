package com.javacafe.realtimewinnmin.common.exception

import com.javacafe.realtimewinnmin.common.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(GlobalException::class)
    protected fun <T> handleGlobalException(e: GlobalException): ResponseEntity<ApiResponse<T>> {
        val response = ApiResponse.error<T>(
            message = e.message ?: "Global Exception",
        )
        return ResponseEntity(response, e.exceptionCode.httpStatus)
    }

}