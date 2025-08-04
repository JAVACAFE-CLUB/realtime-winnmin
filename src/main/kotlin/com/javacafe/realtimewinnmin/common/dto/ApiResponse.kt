package com.javacafe.realtimewinnmin.common.dto

import org.springframework.http.HttpStatus
import java.time.LocalDateTime

data class ApiResponse<T>(
    val code: HttpStatus,
    val data: T? = null,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun <T> success(
                code: HttpStatus = HttpStatus.OK,
                data: T,
                message: String = "성공"
        ): ApiResponse<T> {
            return ApiResponse(
                code = code,
                data = data,
                message = message
            )
        }

        fun error(
            code: HttpStatus = HttpStatus.BAD_REQUEST,
            message: String = "오류가 발생했습니다"
        ): ApiResponse<Nothing> {
            return ApiResponse(
                code = code,
                data = null,
                message = message
            )
        }
    }
}
