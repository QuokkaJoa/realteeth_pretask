package com.quokkajoa.pretask_realteeth.service

import com.quokkajoa.pretask_realteeth.domain.Job
import com.quokkajoa.pretask_realteeth.domain.JobStatus
import com.quokkajoa.pretask_realteeth.dto.JobResponse
import com.quokkajoa.pretask_realteeth.dto.JobSearchParams
import com.quokkajoa.pretask_realteeth.dto.PagedResponse
import com.quokkajoa.pretask_realteeth.exception.JobNotFoundException
import com.quokkajoa.pretask_realteeth.exception.JobStateInconsistencyException
import com.quokkajoa.pretask_realteeth.repository.JobRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class JobService(
    private val jobRepository: JobRepository,
) {
    private val log = LoggerFactory.getLogger(JobService::class.java)

    fun submitJob(idempotencyKey: String, imageUrl: String): JobResponse {
        jobRepository.findByIdempotencyKey(idempotencyKey)?.let {
            log.info("이미 접수된 작업입니다. IdempotencyKey: {}", idempotencyKey)
            return JobResponse.from(it)
        }

        val newJob = Job(idempotencyKey = idempotencyKey, imageUrl = imageUrl)

        return try {
            val savedJob = jobRepository.saveAndFlush(newJob)
            log.info("새로운 작업이 접수되었습니다. Job ID: {}", savedJob.id)
            JobResponse.from(savedJob)
        } catch (e: DataIntegrityViolationException) {
            log.warn("동시 요청으로 인한 중복 저장 시도 방어됨. IdempotencyKey: {}", idempotencyKey)
            val existingJob = jobRepository.findByIdempotencyKey(idempotencyKey)
                ?: throw JobStateInconsistencyException("중복 키 에러가 발생했으나 데이터를 찾을 수 없습니다.")
            JobResponse.from(existingJob)
        }
    }

    @Transactional(readOnly = true)
    fun getJob(id: Long): JobResponse {
        val job = jobRepository.findById(id)
            .orElseThrow { JobNotFoundException(id) }
        return JobResponse.from(job)
    }

    @Transactional(readOnly = true)
    fun findJobs(params: JobSearchParams): PagedResponse<JobResponse> {
        val pageable = params.toPageable()

        val jobPage = if (params.status != null) {
            jobRepository.findByStatus(params.status, pageable)
        } else {
            jobRepository.findAll(pageable)
        }

        return PagedResponse.from(jobPage.map { JobResponse.from(it) })
    }

    @Transactional(readOnly = true)
    fun findPendingJobs(pageable: Pageable): List<Job> =
        jobRepository.findByStatus(JobStatus.PENDING, pageable).content

    @Transactional(readOnly = true)
    fun findProcessingJobs(pageable: Pageable): List<Job> =
        jobRepository.findByStatus(JobStatus.PROCESSING, pageable).content

    @Transactional
    fun updateJobAfterDispatch(id: Long, workerJobId: String, status: JobStatus) {
        val job = jobRepository.findById(id)
            .orElseThrow { JobNotFoundException(id) }

        job.startProcessing(workerJobId)

        if (status == JobStatus.FAILED) {
            job.fail()
        }
    }

    @Transactional
    fun updateJobAfterPolling(id: Long, status: JobStatus, result: String?) {
        val job = jobRepository.findById(id)
            .orElseThrow { JobNotFoundException(id) }

        when (status) {
            JobStatus.COMPLETED -> job.complete(result)
            JobStatus.FAILED -> job.fail()
            else -> {}
        }
    }

    @Transactional
    fun failJob(id: Long) {
        val job = jobRepository.findById(id)
            .orElseThrow { JobNotFoundException(id) }
        job.fail()
    }

    @Transactional(readOnly = true)
    fun findTimeoutJobs(timeoutMinutes: Long, pageable: Pageable): List<Job> =
        jobRepository.findByStatusAndUpdatedAtBefore(
            JobStatus.PROCESSING,
            LocalDateTime.now().minusMinutes(timeoutMinutes),
            pageable
        )

    @Transactional
    fun failJobByTimeout(id: Long) {
        val job = jobRepository.findById(id)
            .orElseThrow { JobNotFoundException(id) }
        if (job.isTimeout()) {
            job.fail()
            log.warn("Job ID {} 타임아웃으로 FAILED 처리.", id)
        }
    }
}
