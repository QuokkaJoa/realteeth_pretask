package com.quokkajoa.pretask_realteeth.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class MockWorkerClientTest {

    @Autowired
    private lateinit var mockWorkerClient: MockWorkerClient

    private val testImageUrl = "https://example.com/test-image.jpg"

    @Test
    fun `API Key로 요청하면 jobId와 PROCESSING 상태를 반환한다`() {
        val response = mockWorkerClient.startProcess(testImageUrl)
        val workerJobId = response.jobId
        val statusResponse = mockWorkerClient.getStatus(workerJobId)

        assertNotNull(response.jobId, "jobId는 null이 아니어야 합니다.")
        assertEquals("PROCESSING", response.status, "초기 상태는 PROCESSING이어야 합니다.")
        assertNotNull(statusResponse.status)

        println("상태 조회 성공 - Job ID: ${statusResponse.jobId}, Status: ${statusResponse.status}")
    }
}
