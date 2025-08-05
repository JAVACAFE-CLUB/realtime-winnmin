package com.javacafe.realtimewinnmin.common.dto

import org.springframework.http.HttpStatus
import java.time.LocalDateTime

data class ApiResponse<T>(
    val data: T? = null,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun <T> success(
                data: T,
                message: String = "성공"
        ): ApiResponse<T> {
            return ApiResponse(
                data = data,
                message = message
            )
        }

        fun error(
            message: String = "오류가 발생했습니다"
        ): ApiResponse<Nothing> {
            return ApiResponse(
                data = null,
                message = message
            )
        }
    }
}
