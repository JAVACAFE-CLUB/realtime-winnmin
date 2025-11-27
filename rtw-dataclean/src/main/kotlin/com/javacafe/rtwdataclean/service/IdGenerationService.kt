package com.javacafe.rtwdataclean.service

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Snowflake 알고리즘 기반 분산 ID 생성
 */
@Service
class IdGenerationService {

    private val workerId: Long = (System.getenv("WORKER_ID")?.toLongOrNull()
        ?: (System.getenv("HOSTNAME")?.hashCode()?.toLong()?.and(0x1F) ?: 1L)) and 0x1F

    private var sequence = 0L
    private var lastTimestamp = -1L

    companion object {
        private const val EPOCH = 1704067200000L // 2024-01-01 00:00:00 UTC
        private const val WORKER_ID_BITS = 5L
        private const val SEQUENCE_BITS = 12L
        private const val MAX_SEQUENCE = (1L shl SEQUENCE_BITS.toInt()) - 1
        private const val WORKER_ID_SHIFT = SEQUENCE_BITS
        private const val TIMESTAMP_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS
    }

    @Synchronized
    fun generateId(): Long {
        var timestamp = currentTimeMillis()

        if (timestamp < lastTimestamp) {
            val offset = lastTimestamp - timestamp
            if (offset <= 5) {
                Thread.sleep(offset)
                timestamp = currentTimeMillis()
            } else {
                throw IllegalStateException("Clock moved backwards by $offset ms")
            }
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) and MAX_SEQUENCE
            if (sequence == 0L) {
                timestamp = waitNextMillis(lastTimestamp)
            }
        } else {
            sequence = 0L
        }

        lastTimestamp = timestamp

        return ((timestamp - EPOCH) shl TIMESTAMP_SHIFT.toInt()) or
                (workerId shl WORKER_ID_SHIFT.toInt()) or
                sequence
    }

    private fun waitNextMillis(lastTimestamp: Long): Long {
        var timestamp = currentTimeMillis()
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis()
        }
        return timestamp
    }

    private fun currentTimeMillis(): Long = Instant.now().toEpochMilli()
}
