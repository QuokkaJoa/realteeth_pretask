package com.quokkajoa.pretask_realteeth.dto

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.quokkajoa.pretask_realteeth.domain.JobStatus
import org.slf4j.LoggerFactory

enum class WorkerStatus {
    PROCESSING,
    COMPLETED,
    FAILED,
    @JsonEnumDefaultValue
    UNKNOWN
}

data class ProcessRequest(val imageUrl: String)

data class ProcessStartResponse(
    val jobId: String,
    val status: WorkerStatus
)

data class ProcessStatusResponse(
    val jobId: String,
    val status: WorkerStatus,
    val result: String? = null
)

private object WorkerStatusMapper {
    val log = LoggerFactory.getLogger(WorkerStatusMapper::class.java)
}

fun WorkerStatus.toDomainStatus(): JobStatus {
    return when (this) {
        WorkerStatus.PROCESSING -> JobStatus.PROCESSING
        WorkerStatus.COMPLETED -> JobStatus.COMPLETED
        WorkerStatus.FAILED -> JobStatus.FAILED
        WorkerStatus.UNKNOWN -> {
            WorkerStatusMapper.log.error("Mock Worker로부터 알 수 없는 상태값(UNKNOWN)을 수신했습니다. FAILED로 폴백합니다.")
            JobStatus.FAILED
        }
    }
}
