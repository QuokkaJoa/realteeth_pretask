package com.quokkajoa.pretask_realteeth.config

import com.quokkajoa.pretask_realteeth.exception.ExternalClientException
import com.quokkajoa.pretask_realteeth.exception.ExternalServiceException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class RestClientConfig {

    @Bean
    fun restClientBuilder(): RestClient.Builder {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()

        val factory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(60))
        }

        return RestClient.builder()
            .requestFactory(factory)
            .defaultStatusHandler({ it.is4xxClientError }) { _, response ->
                throw ExternalClientException(response.statusCode.value(), "외부 API 요청 부적절")
            }
            .defaultStatusHandler({ it.is5xxServerError }) { _, response ->
                throw ExternalServiceException(response.statusCode.value(), "외부 서버 장애 발생")
            }
    }
}
