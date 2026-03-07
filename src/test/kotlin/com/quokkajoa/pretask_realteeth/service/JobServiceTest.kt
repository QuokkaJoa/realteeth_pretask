package com.quokkajoa.pretask_realteeth.service

import com.quokkajoa.pretask_realteeth.domain.JobStatus
import com.quokkajoa.pretask_realteeth.exception.JobNotFoundException
import com.quokkajoa.pretask_realteeth.repository.JobRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
class JobServiceTest {

    @Autowired
    private lateinit var jobService: JobService

    @Autowired
    private lateinit var jobRepository: JobRepository

    @BeforeEach
    fun setUp() {
        jobRepository.deleteAllInBatch()
    }

    @Test
    fun `submitJob - 새로운 작업을 접수하면 PENDING 상태로 저장되고 JobResponse를 반환한다`() {
        val idempotencyKey = "unique-key-1"
        val imageUrl = "https://example.com/image1.jpg"

        val response = jobService.submitJob(idempotencyKey, imageUrl)

        assertNotNull(response.id)
        assertEquals(idempotencyKey, response.idempotencyKey)
        assertEquals(JobStatus.PENDING, response.status)
        assertEquals(1, jobRepository.count())
    }

    @Test
    fun `submitJob - 100개의 스레드가 동일한 idempotencyKey로 동시에 요청해도 DB에는 1건만 저장된다`() {
        val idempotencyKey = "concurrent-key"
        val imageUrl = "https://example.com/image.jpg"

        val threadCount = 100
        val executor = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    jobService.submitJob(idempotencyKey, imageUrl)
                    successCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()

        assertEquals(threadCount, successCount.get())
        assertEquals(1, jobRepository.count())
    }

    @Test
    fun `getJob - 존재하는 Job ID로 조회하면 올바른 JobResponse를 반환한다`() {
        val submitted = jobService.submitJob("get-key", "https://example.com/image.jpg")

        val found = jobService.getJob(submitted.id)

        assertEquals(submitted.id, found.id)
        assertEquals("get-key", found.idempotencyKey)
        assertEquals(JobStatus.PENDING, found.status)
    }

    @Test
    fun `getJob - 존재하지 않는 Job ID로 조회하면 JobNotFoundException을 던진다`() {
        assertThrows<JobNotFoundException> {
            jobService.getJob(999L)
        }
    }
}
