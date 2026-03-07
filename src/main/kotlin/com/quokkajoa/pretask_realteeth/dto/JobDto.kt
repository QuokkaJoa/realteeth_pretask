package com.quokkajoa.pretask_realteeth.dto

import com.quokkajoa.pretask_realteeth.domain.Job
import com.quokkajoa.pretask_realteeth.domain.JobStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

data class JobSubmitRequest(
    @field:NotBlank(message = "imageUrl은 필수입니다.")
    @field:Pattern(
        regexp = "^https?://.+",
        message = "imageUrl은 http 또는 https로 시작하는 URL이어야 합니다."
    )
    val imageUrl: String,

    @field:NotBlank(message = "idempotencyKey는 필수입니다.")
    val idempotencyKey: String
)

data class JobSearchParams(
    val status: JobStatus? = null,

    @field:Min(value = 0, message = "page는 0 이상이어야 합니다.")
    val page: Int = 0,

    @field:Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @field:Max(value = 100, message = "size는 100을 초과할 수 없습니다.")
    val size: Int = 20
) {
    fun toPageable(): Pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
}

data class JobResponse(
    val id: Long,
    val idempotencyKey: String,
    val status: JobStatus,
    val result: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(job: Job): JobResponse {
            return JobResponse(
                id = requireNotNull(job.id) {"Job이 영속화되지 않았습니다."},
                idempotencyKey = job.idempotencyKey,
                status = job.status,
                result = job.result,
                createdAt = job.createdAt,
                updatedAt = job.updatedAt
            )
        }
    }
}
