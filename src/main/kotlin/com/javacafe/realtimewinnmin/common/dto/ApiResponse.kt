package com.javacafe.realtimewinnmin.common.dto

import java.time.LocalDateTime

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun <T> success(data: T?, message: String = "성공"): ApiResponse<T> {
            return ApiResponse(success = true, data = data, message = message)
        }

        fun <T> error(message: String = "오류가 발생했습니다"): ApiResponse<T> {
            return ApiResponse(success = false, data = null, message = message)
        }
    }
}
