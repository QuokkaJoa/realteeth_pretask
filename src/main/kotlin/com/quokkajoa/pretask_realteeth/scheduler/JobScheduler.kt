package com.quokkajoa.pretask_realteeth.scheduler

import com.quokkajoa.pretask_realteeth.component.MockWorkerClient
import com.quokkajoa.pretask_realteeth.domain.Job
import com.quokkajoa.pretask_realteeth.domain.JobStatus
import com.quokkajoa.pretask_realteeth.dto.toDomainStatus
import com.quokkajoa.pretask_realteeth.exception.ExternalClientException
import com.quokkajoa.pretask_realteeth.exception.ExternalRateLimitException
import com.quokkajoa.pretask_realteeth.service.JobService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class JobScheduler(
    private val mockWorkerClient: MockWorkerClient,
    private val jobService: JobService
) {
    private val log = LoggerFactory.getLogger(JobScheduler::class.java)
    private val CHUNK_SIZE = 100

    @Scheduled(fixedDelay = 1000)
    fun dispatchPendingJobs() {
        val pendingJobs = jobService.findPendingJobs(PageRequest.of(0, CHUNK_SIZE))
        if (pendingJobs.isEmpty()) return

        log.info("PENDING 작업 {}건 발견. Mock Worker 전송 시작.", pendingJobs.size)

        for (job in pendingJobs) {
            val jobId = job.id
            if (jobId == null) {
                log.error("영속화되지 않은 Job이 조회되었습니다. 건너뜁니다.")
                continue
            }
            try {
                val response = mockWorkerClient.startProcess(job.imageUrl)
                val status = response.status.toDomainStatus()
                jobService.updateJobAfterDispatch(jobId, response.jobId, status)
            } catch (e: ExternalRateLimitException) {
                log.warn("429 수신. 이번 사이클 전송 중단.")
                break
            } catch (e: ExternalClientException) {
                log.error("Job ID {} 4xx 오류 수신. 즉시 FAILED 처리.", jobId)
                tryFailJob(jobId)
            } catch (e: Exception) {
                log.error("Job ID {} 전송 실패. 다음 사이클 재시도.", jobId)
            }
        }
    }

    @Scheduled(fixedDelay = 5000)
    fun pollProcessingJobs() {
        val processingJobs = jobService.findProcessingJobs(PageRequest.of(0, CHUNK_SIZE))
        if (processingJobs.isEmpty()) return

        log.info("PROCESSING 작업 {}건 발견. 상태 조회 시작.", processingJobs.size)

        for (job in processingJobs) {
            val jobId = job.id
            if (jobId == null) {
                log.error("영속화되지 않은 Job이 조회되었습니다. 건너뜁니다.")
                continue
            }
            val workerJobId = job.workerJobId
            if (workerJobId == null) {
                log.warn("Job ID {} workerJobId 누락. 건너뜁니다.", jobId)
                continue
            }
            try {
                val response = mockWorkerClient.getStatus(workerJobId)
                val status = response.status.toDomainStatus()
                if (status == JobStatus.PROCESSING) continue
                jobService.updateJobAfterPolling(jobId, status, response.result)
            } catch (e: ExternalRateLimitException) {
                log.warn("429 수신. 이번 사이클 폴링 중단.")
                break
            } catch (e: ExternalClientException) {
                log.error("Job ID {} 4xx 오류 수신. 즉시 FAILED 처리.", jobId)
                tryFailJob(jobId)
            } catch (e: Exception) {
                log.error("Job ID {} 폴링 실패. 다음 사이클 재시도.", jobId)
            }
        }
    }

    private fun tryFailJob(jobId: Long) {
        try {
            jobService.failJob(jobId)
        } catch (e: Exception) {
            log.error("Job ID {} FAILED 전이 중 오류: {}", jobId, e.message)
        }
    }

    @Scheduled(fixedDelay = 60_000)
    fun failTimeoutJobs() {
        val timeoutJobs = jobService.findTimeoutJobs(
            timeoutMinutes = 10,
            pageable = PageRequest.of(0, CHUNK_SIZE)
        )
        if (timeoutJobs.isEmpty()) return

        log.warn("타임아웃 작업 {}건 발견. FAILED 처리 시작.", timeoutJobs.size)

        for (job in timeoutJobs) {
            val jobId = job.id ?: continue
            try {
                jobService.failJobByTimeout(jobId)
            } catch (e: Exception) {
                log.error("Job ID {} 타임아웃 FAILED 처리 중 오류: {}", jobId, e.message)
            }
        }
    }
}
