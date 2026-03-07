package com.quokkajoa.pretask_realteeth.repository

import com.quokkajoa.pretask_realteeth.domain.Job
import com.quokkajoa.pretask_realteeth.domain.JobStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.LocalDateTime

@DataJpaTest
@EnableJpaAuditing
class JobRepositoryTest {

    @Autowired
    private lateinit var jobRepository: JobRepository

    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    @BeforeEach
    fun setUp() {
        jobRepository.deleteAllInBatch()
    }

    @Test
    fun `idempotencyKey로 Job을 조회할 수 있다`() {
        val job = jobRepository.save(
            Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        )

        val found = jobRepository.findByIdempotencyKey("key-1")

        assertNotNull(found)
        assertEquals(job.id, found!!.id)
        assertEquals("key-1", found.idempotencyKey)
    }

    @Test
    fun `존재하지 않는 idempotencyKey 조회 시 null을 반환한다`() {
        val result = jobRepository.findByIdempotencyKey("non-existent-key")

        assertNull(result)
    }

    @Test
    fun `서로 다른 idempotencyKey는 각각 독립적으로 조회된다`() {
        jobRepository.save(Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg"))
        jobRepository.save(Job(idempotencyKey = "key-2", imageUrl = "https://example.com/2.jpg"))

        val result1 = jobRepository.findByIdempotencyKey("key-1")
        val result2 = jobRepository.findByIdempotencyKey("key-2")

        assertNotNull(result1)
        assertNotNull(result2)
        assertNotEquals(result1!!.id, result2!!.id)
    }

    @Test
    fun `동일한 idempotencyKey로 저장 시 DataIntegrityViolationException이 발생한다`() {
        jobRepository.save(
            Job(idempotencyKey = "duplicate-key", imageUrl = "https://example.com/1.jpg")
        )

        assertThrows<DataIntegrityViolationException> {
            jobRepository.saveAndFlush(
                Job(idempotencyKey = "duplicate-key", imageUrl = "https://example.com/2.jpg")
            )
        }
    }

    @Test
    fun `PENDING 상태의 Job만 조회된다`() {
        jobRepository.save(Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg"))
        jobRepository.save(Job(idempotencyKey = "key-2", imageUrl = "https://example.com/2.jpg"))

        val processingJob = Job(idempotencyKey = "key-3", imageUrl = "https://example.com/3.jpg")
        processingJob.startProcessing("worker-123")
        jobRepository.save(processingJob)

        val result = jobRepository.findByStatus(
            JobStatus.PENDING,
            PageRequest.of(0, 10)
        )

        assertEquals(2, result.totalElements)
        assertTrue(result.content.all { it.status == JobStatus.PENDING })
    }

    @Test
    fun `해당 상태의 Job이 없으면 빈 페이지를 반환한다`() {
        jobRepository.save(Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg"))

        val result = jobRepository.findByStatus(
            JobStatus.COMPLETED,
            PageRequest.of(0, 10)
        )

        assertEquals(0, result.totalElements)
        assertTrue(result.content.isEmpty())
    }

    @Test
    fun `페이지 크기를 초과하는 데이터는 다음 페이지로 분리된다`() {
        repeat(5) { i ->
            jobRepository.save(
                Job(idempotencyKey = "key-$i", imageUrl = "https://example.com/$i.jpg")
            )
        }

        val firstPage = jobRepository.findByStatus(
            JobStatus.PENDING,
            PageRequest.of(0, 3)
        )
        val secondPage = jobRepository.findByStatus(
            JobStatus.PENDING,
            PageRequest.of(1, 3)
        )

        assertEquals(5, firstPage.totalElements)
        assertEquals(3, firstPage.content.size)
        assertEquals(2, secondPage.content.size)
        assertTrue(firstPage.hasNext())
        assertFalse(secondPage.hasNext())
    }

    @Test
    fun `createdAt 내림차순으로 정렬된다`() {
        repeat(3) { i ->
            jobRepository.save(
                Job(idempotencyKey = "key-$i", imageUrl = "https://example.com/$i.jpg")
            )
        }

        val result = jobRepository.findByStatus(
            JobStatus.PENDING,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        )

        val dates = result.content.map { it.createdAt }
        for (i in 0 until dates.size - 1) {
            assertFalse(dates[i]!!.isBefore(dates[i + 1]))
        }
    }

    @Test
    fun `updatedAt이 기준 시간 이전인 PROCESSING Job이 조회된다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg")
        job.startProcessing("worker-123")
        val saved = jobRepository.saveAndFlush(job)

        testEntityManager.entityManager.createQuery(
            "UPDATE Job j SET j.updatedAt = :updatedAt WHERE j.id = :id"
        )
            .setParameter("updatedAt", LocalDateTime.now().minusMinutes(11))
            .setParameter("id", saved.id)
            .executeUpdate()

        testEntityManager.entityManager.clear()

        val threshold = LocalDateTime.now().minusMinutes(10)
        val result = jobRepository.findByStatusAndUpdatedAtBefore(
            JobStatus.PROCESSING,
            threshold,
            PageRequest.of(0, 10)
        )

        assertEquals(1, result.size)
        assertEquals(saved.id, result[0].id)
    }

    @Test
    fun `updatedAt이 기준 시간 이후인 PROCESSING Job은 조회되지 않는다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg")
        job.startProcessing("worker-123")
        jobRepository.save(job)

        val threshold = LocalDateTime.now().minusMinutes(10)
        val result = jobRepository.findByStatusAndUpdatedAtBefore(
            JobStatus.PROCESSING,
            threshold,
            PageRequest.of(0, 10)
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `PENDING 상태의 Job은 타임아웃 조회에서 제외된다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/1.jpg")
        val saved = jobRepository.save(job)

        val field = Job::class.java.getDeclaredField("updatedAt")
        field.isAccessible = true
        field.set(saved, LocalDateTime.now().minusMinutes(11))
        jobRepository.save(saved)

        val threshold = LocalDateTime.now().minusMinutes(10)
        val result = jobRepository.findByStatusAndUpdatedAtBefore(
            JobStatus.PROCESSING,
            threshold,
            PageRequest.of(0, 10)
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `Job 저장 시 createdAt과 updatedAt이 자동으로 설정된다`() {
        val before = LocalDateTime.now().minusSeconds(1)

        val job = jobRepository.save(
            Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        )

        assertNotNull(job.createdAt)
        assertNotNull(job.updatedAt)
        assertTrue(job.createdAt!!.isAfter(before))
        assertTrue(job.updatedAt!!.isAfter(before))
    }

    @Test
    fun `Job 상태 변경 시 updatedAt이 갱신된다`() {
        val job = jobRepository.saveAndFlush(
            Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        )
        val createdAt = job.updatedAt

        Thread.sleep(10)

        job.startProcessing("worker-123")
        val updated = jobRepository.saveAndFlush(job)

        assertTrue(updated.updatedAt!!.isAfter(createdAt))
    }

    @Test
    fun `Job 저장 후 createdAt은 변경되지 않는다`() {
        val job = jobRepository.saveAndFlush(
            Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        )
        val originalCreatedAt = job.createdAt

        Thread.sleep(10)

        job.startProcessing("worker-123")
        val updated = jobRepository.saveAndFlush(job)

        assertEquals(originalCreatedAt, updated.createdAt)
    }
}
