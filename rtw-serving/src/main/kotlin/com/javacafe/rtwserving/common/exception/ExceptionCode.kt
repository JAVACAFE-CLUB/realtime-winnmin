package com.javacafe.rtwserving.common.exception

import org.springframework.http.HttpStatus

enum class ExceptionCode(val httpStatus: HttpStatus, val message: String) {
    /** Common Error Code */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal server error"),
    BAD_REQUEST_ERROR(HttpStatus.BAD_REQUEST, "bad request"),
    NOT_FOUND_ERROR(HttpStatus.NOT_FOUND, "not found"),
}