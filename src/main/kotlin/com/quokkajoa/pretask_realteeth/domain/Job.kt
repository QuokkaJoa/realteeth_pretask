package com.quokkajoa.pretask_realteeth.domain

import com.quokkajoa.pretask_realteeth.exception.InvalidJobStateException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(
    name = "jobs",
    indexes = [Index(name = "idx_status", columnList = "status")],
    uniqueConstraints = [UniqueConstraint(
        name = "uk_idempotency_key",
        columnNames = ["idempotency_key"]
    )]
)
@EntityListeners(AuditingEntityListener::class)
class Job(
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    val idempotencyKey: String,

    @Column(name = "image_url", nullable = false, updatable = false)
    val imageUrl: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
) {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: JobStatus = JobStatus.PENDING
        protected set

    @Column(name = "worker_job_id")
    var workerJobId: String? = null
        protected set

    @Column(columnDefinition = "TEXT")
    var result: String? = null
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    fun startProcessing(workerJobId: String) {
        if (this.status != JobStatus.PENDING) {
            throw InvalidJobStateException("PENDING 상태의 작업만 시작할 수 있습니다. 현재 상태: ${this.status}")
        }
        this.workerJobId = workerJobId
        this.status = JobStatus.PROCESSING
    }

    fun complete(result: String?) {
        if (this.status != JobStatus.PROCESSING) {
            throw InvalidJobStateException("PROCESSING 상태의 작업만 완료할 수 있습니다. 현재 상태: ${this.status}")
        }
        this.result = result
        this.status = JobStatus.COMPLETED
    }

    fun fail() {
        if (this.status == JobStatus.COMPLETED || this.status == JobStatus.FAILED) {
            throw InvalidJobStateException("이미 종료된 작업(COMPLETED/FAILED)은 실패 처리할 수 없습니다.")
        }
        this.status = JobStatus.FAILED
    }

    fun isTimeout(timeoutMinutes: Long = 10): Boolean {
        return status == JobStatus.PROCESSING &&
                updatedAt.isBefore(LocalDateTime.now().minusMinutes(timeoutMinutes))
    }
}
