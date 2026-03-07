package com.quokkajoa.pretask_realteeth.repository

import com.quokkajoa.pretask_realteeth.domain.Job
import com.quokkajoa.pretask_realteeth.domain.JobStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface JobRepository : JpaRepository<Job, Long> {

    fun findByIdempotencyKey(idempotencyKey: String): Job?

    fun findByStatus(status: JobStatus, pageable: Pageable): Page<Job>

    @Query("""
    SELECT j FROM Job j 
    WHERE j.status = :status 
    AND j.updatedAt < :threshold
""")
    fun findByStatusAndUpdatedAtBefore(
        status: JobStatus,
        threshold: LocalDateTime,
        pageable: Pageable
    ): List<Job>
}
