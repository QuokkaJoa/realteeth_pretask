package com.quokkajoa.pretask_realteeth.config

import com.quokkajoa.pretask_realteeth.exception.ExternalClientException
import com.quokkajoa.pretask_realteeth.exception.ExternalRateLimitException
import com.quokkajoa.pretask_realteeth.exception.ExternalServiceException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class RestClientConfig(
    @Value("\${mock-worker.timeout.connect:5}") private val connectTimeoutSeconds: Long,
    @Value("\${mock-worker.timeout.read:60}") private val readTimeoutSeconds: Long
) {

    @Bean
    fun restClientBuilder(): RestClient.Builder {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
            .build()

        val factory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(readTimeoutSeconds))
        }

        return RestClient.builder()
            .requestFactory(factory)
            .defaultStatusHandler({ it.value() == 429 }) { _, response ->
                val errorBody = String(response.body.readAllBytes())
                throw ExternalRateLimitException(429, "Mock Worker 처리 용량 초과 - 응답: $errorBody")
            }
            .defaultStatusHandler({ it.is4xxClientError }) { _, response ->
                val errorBody = String(response.body.readAllBytes())
                val statusCode = response.statusCode.value()

                throw ExternalClientException(statusCode, "외부 API 요청 부적절 - 응답: $errorBody")
            }
            .defaultStatusHandler({ it.is5xxServerError }) { _, response ->
                val errorBody = String(response.body.readAllBytes())
                val statusCode = response.statusCode.value()
                throw ExternalServiceException(statusCode, "외부 서버 장애 발생 - 응답: $errorBody")
            }
    }
}
