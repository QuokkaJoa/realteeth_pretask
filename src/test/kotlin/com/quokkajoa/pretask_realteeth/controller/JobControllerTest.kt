package com.quokkajoa.pretask_realteeth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.quokkajoa.pretask_realteeth.domain.JobStatus
import com.quokkajoa.pretask_realteeth.dto.JobResponse
import com.quokkajoa.pretask_realteeth.dto.JobSubmitRequest
import com.quokkajoa.pretask_realteeth.dto.PagedResponse
import com.quokkajoa.pretask_realteeth.exception.JobNotFoundException
import com.quokkajoa.pretask_realteeth.service.JobService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(JobController::class)
class JobControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var jobService: JobService

    @Test
    fun `정상 요청 시 202 Accepted와 Location 헤더를 반환한다`() {
        val request = JobSubmitRequest(
            imageUrl = "https://example.com/image.jpg",
            idempotencyKey = "key-001"
        )
        val jobResponse = JobResponse(
            id = 1L,
            idempotencyKey = "key-001",
            status = JobStatus.PENDING,
            result = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        given(jobService.submitJob("key-001", "https://example.com/image.jpg"))
            .willReturn(jobResponse)

        mockMvc.perform(
            post("/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isAccepted)
            .andExpect(header().string("Location", "/jobs/1"))
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.idempotencyKey").value("key-001"))
    }

    @Test
    fun `imageUrl이 비어있으면 400 Bad Request를 반환한다`() {
        val request = JobSubmitRequest(
            imageUrl = "",
            idempotencyKey = "key-001"
        )

        mockMvc.perform(
            post("/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `imageUrl이 http로 시작하지 않으면 400 Bad Request를 반환한다`() {
        val request = JobSubmitRequest(
            imageUrl = "invalid-url-format",
            idempotencyKey = "key-001"
        )

        mockMvc.perform(
            post("/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `idempotencyKey가 비어있으면 400 Bad Request를 반환한다`() {
        val request = JobSubmitRequest(
            imageUrl = "https://example.com/image.jpg",
            idempotencyKey = ""
        )

        mockMvc.perform(
            post("/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `요청 바디가 없으면 400 Bad Request를 반환한다`() {
        mockMvc.perform(
            post("/jobs")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `동일한 idempotencyKey로 재요청하면 기존 Job을 반환한다`() {
        val existingResponse = JobResponse(
            id = 1L,
            idempotencyKey = "key-001",
            status = JobStatus.PROCESSING,
            result = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        val request = JobSubmitRequest(
            imageUrl = "https://example.com/image.jpg",
            idempotencyKey = "key-001"
        )
        given(jobService.submitJob("key-001", "https://example.com/image.jpg"))
            .willReturn(existingResponse)

        mockMvc.perform(
            post("/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.status").value("PROCESSING"))
    }

    @Test
    fun `존재하는 Job 조회 시 200과 JobResponse를 반환한다`() {
        val jobResponse = JobResponse(
            id = 1L,
            idempotencyKey = "key-001",
            status = JobStatus.COMPLETED,
            result = "처리 결과",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        given(jobService.getJob(1L)).willReturn(jobResponse)

        mockMvc.perform(get("/jobs/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.result").value("처리 결과"))
    }

    @Test
    fun `존재하지 않는 Job 조회 시 404를 반환한다`() {
        given(jobService.getJob(999L))
            .willThrow(JobNotFoundException(999L))

        mockMvc.perform(get("/jobs/999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `목록 조회 시 200과 PagedResponse를 반환한다`() {
        val pagedResponse = PagedResponse(
            content = listOf(
                JobResponse(
                    id = 1L,
                    idempotencyKey = "key-001",
                    status = JobStatus.PENDING,
                    result = null,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            ),
            page = 0,
            size = 20,
            totalElements = 1,
            totalPages = 1,
            hasNext = false
        )
        given(jobService.findJobs(any())).willReturn(pagedResponse)

        mockMvc.perform(get("/jobs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].status").value("PENDING"))
            .andExpect(jsonPath("$.hasNext").value(false))
    }

    @Test
    fun `status 필터로 목록 조회 시 해당 상태만 반환한다`() {
        val pagedResponse = PagedResponse(
            content = emptyList<JobResponse>(),
            page = 0,
            size = 20,
            totalElements = 0,
            totalPages = 0,
            hasNext = false
        )
        given(jobService.findJobs(any())).willReturn(pagedResponse)

        mockMvc.perform(get("/jobs?status=COMPLETED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `size가 100을 초과하면 400 Bad Request를 반환한다`() {
        mockMvc.perform(get("/jobs?size=101"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `page가 음수이면 400 Bad Request를 반환한다`() {
        mockMvc.perform(get("/jobs?page=-1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `유효하지 않은 status 값으로 조회 시 400 Bad Request를 반환한다`() {
        mockMvc.perform(get("/jobs?status=INVALID_STATUS"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }
}
