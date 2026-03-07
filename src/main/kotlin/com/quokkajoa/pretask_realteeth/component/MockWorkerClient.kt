package com.quokkajoa.pretask_realteeth.component

import com.quokkajoa.pretask_realteeth.dto.ProcessRequest
import com.quokkajoa.pretask_realteeth.dto.ProcessStartResponse
import com.quokkajoa.pretask_realteeth.dto.ProcessStatusResponse
import com.quokkajoa.pretask_realteeth.exception.ExternalServiceException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class MockWorkerClient(
    private val restClientBuilder: RestClient.Builder,
    @Value("\${mock-worker.base-url}") private val baseUrl: String,
    @Value("\${mock-worker.api-key}") private val apiKey: String
) {
    private val client by lazy {
        restClientBuilder.clone().baseUrl(baseUrl).build()
    }

    fun startProcess(imageUrl: String):
            ProcessStartResponse {
        return client.post()
            .uri("/process")
            .header("X-API-KEY", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ProcessRequest(imageUrl))
            .retrieve()
            .body<ProcessStartResponse>()
            ?: throw ExternalServiceException(500,"Mock Worker 응답 본문이 비어있습니다.")
        }

    fun getStatus(workerJobId: String): ProcessStatusResponse {
        return client.get()
            .uri("/process/{job_id}", workerJobId)
            .header("X-API-KEY", apiKey)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body<ProcessStatusResponse>()
            ?: throw ExternalServiceException(500, "Mock Worker 상태 조회 응답이 비어있습니다. JobId: $workerJobId")
    }
}
