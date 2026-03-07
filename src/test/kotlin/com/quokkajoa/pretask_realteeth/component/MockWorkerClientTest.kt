package com.quokkajoa.pretask_realteeth.component

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.quokkajoa.pretask_realteeth.config.RestClientConfig
import com.quokkajoa.pretask_realteeth.dto.WorkerStatus
import com.quokkajoa.pretask_realteeth.exception.ExternalClientException
import com.quokkajoa.pretask_realteeth.exception.ExternalRateLimitException
import com.quokkajoa.pretask_realteeth.exception.ExternalServiceException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MockWorkerClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockWorkerClient: MockWorkerClient
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()

        val restClientConfig = RestClientConfig(
            connectTimeoutSeconds = 5,
            readTimeoutSeconds = 10
        )
        val builder = restClientConfig.restClientBuilder()

        mockWorkerClient = MockWorkerClient(
            restClientBuilder = builder,
            baseUrl = baseUrl,
            apiKey = "test-api-key"
        )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `정상 요청 시 jobId와 PROCESSING 상태를 반환한다`() {
        val responseBody = objectMapper.writeValueAsString(
            mapOf("jobId" to "worker-job-123", "status" to "PROCESSING")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody)
        )

        val response = mockWorkerClient.startProcess("https://example.com/image.jpg")

        assertEquals("worker-job-123", response.jobId)
        assertEquals(WorkerStatus.PROCESSING, response.status)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/process", recordedRequest.path)
        assertEquals("test-api-key", recordedRequest.getHeader("X-API-KEY"))
        assertEquals("POST", recordedRequest.method)
    }

    @Test
    fun `상태 조회 시 ProcessStatusResponse를 올바르게 파싱한다`() {
        val responseBody = objectMapper.writeValueAsString(
            mapOf("jobId" to "worker-job-123", "status" to "COMPLETED", "result" to "처리 결과")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody)
        )

        val response = mockWorkerClient.getStatus("worker-job-123")

        assertEquals(WorkerStatus.COMPLETED, response.status)
        assertEquals("처리 결과", response.result)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/process/worker-job-123", recordedRequest.path)
        assertEquals("GET", recordedRequest.method)
    }

    @Test
    fun `429 응답 시 ExternalRateLimitException을 던진다`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Too Many Requests"}""")
        )

        assertThrows<ExternalRateLimitException> {
            mockWorkerClient.startProcess("https://example.com/image.jpg")
        }
    }

    @Test
    fun `4xx 응답 시 ExternalClientException을 던진다`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Bad Request"}""")
        )

        assertThrows<ExternalClientException> {
            mockWorkerClient.startProcess("https://example.com/image.jpg")
        }
    }

    @Test
    fun `5xx 응답 시 ExternalServiceException을 던진다`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Internal Server Error"}""")
        )

        assertThrows<ExternalServiceException> {
            mockWorkerClient.startProcess("https://example.com/image.jpg")
        }
    }
}
