package com.quokkajoa.pretask_realteeth.domain

import com.quokkajoa.pretask_realteeth.exception.InvalidJobStateException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class JobEntityTest {

    @Test
    fun `Job 생성 시 초기 상태는 PENDING이다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")

        assertEquals(JobStatus.PENDING, job.status)
        assertNull(job.workerJobId)
        assertNull(job.result)
    }

    @Test
    fun `PENDING 상태에서 startProcessing 호출 시 PROCESSING으로 전이된다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")

        job.startProcessing("worker-123")

        assertEquals(JobStatus.PROCESSING, job.status)
        assertEquals("worker-123", job.workerJobId)
    }

    @Test
    fun `PROCESSING 상태에서 startProcessing 호출 시 예외가 발생한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")

        val exception = assertThrows<InvalidJobStateException> {
            job.startProcessing("worker-456")
        }
        assertTrue(exception.message!!.contains("PENDING 상태의 작업만 시작할 수 있습니다"))
    }

    @Test
    fun `COMPLETED 상태에서 startProcessing 호출 시 예외가 발생한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")
        job.complete("result")

        assertThrows<InvalidJobStateException> {
            job.startProcessing("worker-456")
        }
    }

    @Test
    fun `FAILED 상태에서 startProcessing 호출 시 예외가 발생한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")
        job.fail()

        assertThrows<InvalidJobStateException> {
            job.startProcessing("worker-456")
        }
    }

    @Test
    fun `PROCESSING 상태에서 complete 호출 시 COMPLETED로 전이된다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")

        job.complete("result-data")

        assertEquals(JobStatus.COMPLETED, job.status)
        assertEquals("result-data", job.result)
    }

    @Test
    fun `PROCESSING 상태에서 complete 호출 시 result가 null이어도 COMPLETED로 전이된다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")

        job.complete(null)

        assertEquals(JobStatus.COMPLETED, job.status)
        assertNull(job.result)
    }

    @Test
    fun `PENDING 상태에서 complete 호출 시 예외가 발생한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")

        val exception = assertThrows<InvalidJobStateException> {
            job.complete("result")
        }
        assertTrue(exception.message!!.contains("PROCESSING 상태의 작업만 완료할 수 있습니다"))
    }

    @Test
    fun `COMPLETED 상태에서 complete 호출 시 예외가 발생한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")
        job.complete("result")

        assertThrows<InvalidJobStateException> {
            job.complete("result-again")
        }
    }

    @Test
    fun `FAILED 상태에서 complete 호출 시 예외가 발생한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")
        job.fail()

        assertThrows<InvalidJobStateException> {
            job.complete("result")
        }
    }

    @Test
    fun `PENDING 상태에서 fail 호출 시 FAILED로 전이된다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")

        job.fail()

        assertEquals(JobStatus.FAILED, job.status)
    }

    @Test
    fun `PROCESSING 상태에서 fail 호출 시 FAILED로 전이된다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")

        job.fail()

        assertEquals(JobStatus.FAILED, job.status)
    }

    @Test
    fun `COMPLETED 상태에서 fail 호출 시 예외가 발생한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")
        job.complete("result")

        val exception = assertThrows<InvalidJobStateException> {
            job.fail()
        }
        assertTrue(exception.message!!.contains("이미 종료된 작업"))
    }

    @Test
    fun `FAILED 상태에서 fail 호출 시 예외가 발생한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")
        job.fail()

        val exception = assertThrows<InvalidJobStateException> {
            job.fail()
        }
        assertTrue(exception.message!!.contains("이미 종료된 작업"))
    }

    @Test
    fun `PROCESSING 상태이고 updatedAt이 기준 시간 이전이면 isTimeout이 true를 반환한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")

        val field = Job::class.java.getDeclaredField("updatedAt")
        field.isAccessible = true
        field.set(job, LocalDateTime.now().minusMinutes(11))

        assertTrue(job.isTimeout(10))
    }

    @Test
    fun `PROCESSING 상태이고 updatedAt이 기준 시간 이내이면 isTimeout이 false를 반환한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")

        assertFalse(job.isTimeout(10))
    }

    @Test
    fun `PENDING 상태이면 isTimeout이 false를 반환한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")

        assertFalse(job.isTimeout(10))
    }

    @Test
    fun `COMPLETED 상태이면 isTimeout이 false를 반환한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")
        job.complete("result")

        assertFalse(job.isTimeout(10))
    }

    @Test
    fun `FAILED 상태이면 isTimeout이 false를 반환한다`() {
        val job = Job(idempotencyKey = "key-1", imageUrl = "https://example.com/image.jpg")
        job.startProcessing("worker-123")
        job.fail()

        assertFalse(job.isTimeout(10))
    }
}
