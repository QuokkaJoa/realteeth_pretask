package com.quokkajoa.pretask_realteeth.scheduler

import com.quokkajoa.pretask_realteeth.component.MockWorkerClient
import com.quokkajoa.pretask_realteeth.domain.Job
import com.quokkajoa.pretask_realteeth.domain.JobStatus
import com.quokkajoa.pretask_realteeth.dto.ProcessStartResponse
import com.quokkajoa.pretask_realteeth.dto.ProcessStatusResponse
import com.quokkajoa.pretask_realteeth.dto.WorkerStatus
import com.quokkajoa.pretask_realteeth.exception.ExternalClientException
import com.quokkajoa.pretask_realteeth.exception.ExternalRateLimitException
import com.quokkajoa.pretask_realteeth.exception.ExternalServiceException
import com.quokkajoa.pretask_realteeth.service.JobService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class JobSchedulerTest {

    @Mock
    private lateinit var mockWorkerClient: MockWorkerClient

    @Mock
    private lateinit var jobService: JobService

    @InjectMocks
    private lateinit var jobScheduler: JobScheduler

    @Test
    fun `PENDING 작업이 없으면 Mock Worker에 요청하지 않는다`() {
        given(jobService.findPendingJobs(any())).willReturn(emptyList())

        jobScheduler.dispatchPendingJobs()

        verify(mockWorkerClient, never()).startProcess(any())
    }

    @Test
    fun `PENDING 작업이 있으면 Mock Worker로 전송하고 상태를 업데이트한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)
        val response = ProcessStartResponse(jobId = "worker-1", status = WorkerStatus.PROCESSING)

        given(jobService.findPendingJobs(any())).willReturn(listOf(job))
        given(mockWorkerClient.startProcess("https://example.com/1.jpg")).willReturn(response)

        jobScheduler.dispatchPendingJobs()

        verify(jobService).updateJobAfterDispatch(1L, "worker-1", JobStatus.PROCESSING)
    }

    @Test
    fun `Mock Worker가 즉시 FAILED를 반환하면 FAILED 상태로 업데이트한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)
        val response = ProcessStartResponse(jobId = "worker-1", status = WorkerStatus.FAILED)

        given(jobService.findPendingJobs(any())).willReturn(listOf(job))
        given(mockWorkerClient.startProcess(any())).willReturn(response)

        jobScheduler.dispatchPendingJobs()

        verify(jobService).updateJobAfterDispatch(1L, "worker-1", JobStatus.FAILED)
    }

    @Test
    fun `429 수신 시 해당 사이클을 즉시 중단하고 이후 작업은 처리하지 않는다`() {
        val job1 = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)
        val job2 = Job(idempotencyKey = "key-2", imageUrl = "https://example.com/2.jpg", id = 2L)

        given(jobService.findPendingJobs(any())).willReturn(listOf(job1, job2))
        given(mockWorkerClient.startProcess("https://example.com/1.jpg"))
            .willThrow(ExternalRateLimitException(429, "Too Many Requests"))

        jobScheduler.dispatchPendingJobs()

        verify(mockWorkerClient, times(1)).startProcess(any())
        verify(mockWorkerClient, never()).startProcess("https://example.com/2.jpg")
        verify(jobService, never()).updateJobAfterDispatch(eq(2L), any(), any())
    }

    @Test
    fun `4xx 수신 시 해당 작업을 FAILED 처리하고 다음 작업은 계속 진행한다`() {
        val job1 = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/bad.jpg", id = 1L)
        val job2 = Job(idempotencyKey = "key-2", imageUrl = "https://example.com/good.jpg", id = 2L)
        val response = ProcessStartResponse(jobId = "worker-2", status = WorkerStatus.PROCESSING)

        given(jobService.findPendingJobs(any())).willReturn(listOf(job1, job2))
        given(mockWorkerClient.startProcess("https://example.com/bad.jpg"))
            .willThrow(ExternalClientException(400, "Bad Request"))
        given(mockWorkerClient.startProcess("https://example.com/good.jpg"))
            .willReturn(response)

        jobScheduler.dispatchPendingJobs()

        verify(jobService).failJob(1L)
        verify(jobService).updateJobAfterDispatch(2L, "worker-2", JobStatus.PROCESSING)
    }

    @Test
    fun `5xx 수신 시 해당 작업을 FAILED 처리하지 않고 다음 사이클에 재시도한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)

        given(jobService.findPendingJobs(any())).willReturn(listOf(job))
        given(mockWorkerClient.startProcess(any()))
            .willThrow(ExternalServiceException(500, "Internal Server Error"))

        jobScheduler.dispatchPendingJobs()

        verify(jobService, never()).failJob(any())
        verify(jobService, never()).updateJobAfterDispatch(any(), any(), any())
    }

    @Test
    fun `여러 PENDING 작업이 있으면 모두 순차적으로 처리한다`() {
        val jobs = (1..3).map { i ->
            Job(idempotencyKey = "key-$i", imageUrl = "https://example.com/$i.jpg", id = i.toLong())
        }
        val response = ProcessStartResponse(jobId = "worker-1", status = WorkerStatus.PROCESSING)

        given(jobService.findPendingJobs(any())).willReturn(jobs)
        given(mockWorkerClient.startProcess(any())).willReturn(response)

        jobScheduler.dispatchPendingJobs()

        verify(mockWorkerClient, times(3)).startProcess(any())
        verify(jobService, times(3)).updateJobAfterDispatch(any(), any(), any())
    }

    @Test
    fun `PROCESSING 작업이 없으면 Mock Worker에 요청하지 않는다`() {
        given(jobService.findProcessingJobs(any())).willReturn(emptyList())

        jobScheduler.pollProcessingJobs()

        verify(mockWorkerClient, never()).getStatus(any())
    }

    @Test
    fun `PROCESSING 작업 폴링 중 COMPLETED를 수신하면 상태를 업데이트한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)
            .apply { startProcessing("worker-1") }
        val response = ProcessStatusResponse(
            jobId = "worker-1",
            status = WorkerStatus.COMPLETED,
            result = "처리 결과"
        )

        given(jobService.findProcessingJobs(any())).willReturn(listOf(job))
        given(mockWorkerClient.getStatus("worker-1")).willReturn(response)

        jobScheduler.pollProcessingJobs()

        verify(jobService).updateJobAfterPolling(1L, JobStatus.COMPLETED, "처리 결과")
    }

    @Test
    fun `PROCESSING 작업 폴링 중 FAILED를 수신하면 FAILED 상태로 업데이트한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)
            .apply { startProcessing("worker-1") }
        val response = ProcessStatusResponse(
            jobId = "worker-1",
            status = WorkerStatus.FAILED,
            result = null
        )

        given(jobService.findProcessingJobs(any())).willReturn(listOf(job))
        given(mockWorkerClient.getStatus("worker-1")).willReturn(response)

        jobScheduler.pollProcessingJobs()

        verify(jobService).updateJobAfterPolling(1L, JobStatus.FAILED, null)
    }

    @Test
    fun `PROCESSING 작업 폴링 중 PROCESSING을 수신하면 상태를 변경하지 않는다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)
            .apply { startProcessing("worker-1") }
        val response = ProcessStatusResponse(
            jobId = "worker-1",
            status = WorkerStatus.PROCESSING,
            result = null
        )

        given(jobService.findProcessingJobs(any())).willReturn(listOf(job))
        given(mockWorkerClient.getStatus("worker-1")).willReturn(response)

        jobScheduler.pollProcessingJobs()

        verify(jobService, never()).updateJobAfterPolling(any(), any(), anyOrNull())
    }

    @Test
    fun `폴링 중 429 수신 시 해당 사이클을 즉시 중단하고 이후 작업은 처리하지 않는다`() {
        val job1 = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)
            .apply { startProcessing("worker-1") }
        val job2 = Job(idempotencyKey = "key-2", imageUrl = "https://example.com/2.jpg", id = 2L)
            .apply { startProcessing("worker-2") }

        given(jobService.findProcessingJobs(any())).willReturn(listOf(job1, job2))
        given(mockWorkerClient.getStatus("worker-1"))
            .willThrow(ExternalRateLimitException(429, "Too Many Requests"))

        jobScheduler.pollProcessingJobs()

        verify(mockWorkerClient, times(1)).getStatus(any())
        verify(mockWorkerClient, never()).getStatus("worker-2")
    }

    @Test
    fun `폴링 중 4xx 수신 시 해당 작업을 FAILED 처리하고 다음 작업은 계속 진행한다`() {
        val job1 = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)
            .apply { startProcessing("worker-1") }
        val job2 = Job(idempotencyKey = "key-2", imageUrl = "https://example.com/2.jpg", id = 2L)
            .apply { startProcessing("worker-2") }
        val response = ProcessStatusResponse(
            jobId = "worker-2",
            status = WorkerStatus.COMPLETED,
            result = "결과"
        )

        given(jobService.findProcessingJobs(any())).willReturn(listOf(job1, job2))
        given(mockWorkerClient.getStatus("worker-1"))
            .willThrow(ExternalClientException(404, "Not Found"))
        given(mockWorkerClient.getStatus("worker-2")).willReturn(response)

        jobScheduler.pollProcessingJobs()

        verify(jobService).failJob(1L)
        verify(jobService).updateJobAfterPolling(2L, JobStatus.COMPLETED, "결과")
    }

    @Test
    fun `workerJobId가 null인 작업은 폴링에서 건너뛴다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)

        given(jobService.findProcessingJobs(any())).willReturn(listOf(job))

        jobScheduler.pollProcessingJobs()

        verify(mockWorkerClient, never()).getStatus(any())
    }

    @Test
    fun `타임아웃 작업이 없으면 failJobByTimeout을 호출하지 않는다`() {
        given(jobService.findTimeoutJobs(any(), any())).willReturn(emptyList())

        jobScheduler.failTimeoutJobs()

        verify(jobService, never()).failJobByTimeout(any())
    }

    @Test
    fun `타임아웃 작업이 있으면 FAILED 처리한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)

        given(jobService.findTimeoutJobs(any(), any())).willReturn(listOf(job))

        jobScheduler.failTimeoutJobs()

        verify(jobService).failJobByTimeout(1L)
    }

    @Test
    fun `타임아웃 처리 중 예외가 발생해도 다음 작업은 계속 처리한다`() {
        val job1 = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg", id = 1L)
        val job2 = Job(idempotencyKey = "key-2", imageUrl = "https://example.com/2.jpg", id = 2L)

        given(jobService.findTimeoutJobs(any(), any())).willReturn(listOf(job1, job2))
        given(jobService.failJobByTimeout(1L))
            .willThrow(RuntimeException("예상치 못한 오류"))

        jobScheduler.failTimeoutJobs()

        verify(jobService).failJobByTimeout(1L)
        verify(jobService).failJobByTimeout(2L)
    }
}
