package com.javacafe.rtwserve.common.exception

open class GlobalException (
    val exceptionCode: ExceptionCode,
    override val message: String? = exceptionCode.message,
    val extra: Map<String, Any>? = null,
) : RuntimeException(message ?: exceptionCode.message)