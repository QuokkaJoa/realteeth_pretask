package com.quokkajoa.pretask_realteeth.controller

import com.quokkajoa.pretask_realteeth.dto.ErrorResponse
import com.quokkajoa.pretask_realteeth.exception.ExternalClientException
import com.quokkajoa.pretask_realteeth.exception.ExternalException
import com.quokkajoa.pretask_realteeth.exception.ExternalRateLimitException
import com.quokkajoa.pretask_realteeth.exception.ExternalServiceException
import com.quokkajoa.pretask_realteeth.exception.InvalidJobStateException
import com.quokkajoa.pretask_realteeth.exception.JobNotFoundException
import com.quokkajoa.pretask_realteeth.exception.JobStateInconsistencyException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ExternalRateLimitException::class)
    fun handleRateLimit(e: ExternalRateLimitException): ResponseEntity<ErrorResponse> {
        log.warn("External Rate Limit: {}", e.message)
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "외부 서비스 처리 용량 초과. 잠시 후 재시도하십시오.")
    }

    @ExceptionHandler(ExternalClientException::class)
    fun handleExternalClientError(e: ExternalClientException): ResponseEntity<ErrorResponse> {
        log.error("External Client Error [{}]: {}", e.statusCode, e.message)
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.message ?: "외부 서비스 요청 오류")
    }

    @ExceptionHandler(ExternalServiceException::class)
    fun handleExternalServiceError(e: ExternalServiceException): ResponseEntity<ErrorResponse> {
        log.error("External Service Error [{}]: {}", e.statusCode, e.message)
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, e.message ?: "외부 이미지 처리 서비스 장애")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation Failed: {}", message)
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.warn("Malformed Request Body: {}", e.message)
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "요청 본문을 파싱할 수 없습니다. JSON 형식을 확인하십시오.")
    }

    @ExceptionHandler(Exception::class)
    fun handleAllUncaughtException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unknown Exception: ", e)
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류 발생")
    }

    @ExceptionHandler(JobNotFoundException::class)
    fun handleJobNotFound(e: JobNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Job Not Found: {}", e.message)
        return buildErrorResponse(HttpStatus.NOT_FOUND, e.message ?: "존재하지 않는 작업입니다.")
    }

    @ExceptionHandler(InvalidJobStateException::class)
    fun handleInvalidJobState(e: InvalidJobStateException): ResponseEntity<ErrorResponse> {
        log.warn("Invalid Job State: {}", e.message)
        return buildErrorResponse(HttpStatus.CONFLICT, e.message ?: "허용되지 않는 상태 전이입니다.")
    }

    @ExceptionHandler(JobStateInconsistencyException::class)
    fun handleJobStateInconsistency(e: JobStateInconsistencyException): ResponseEntity<ErrorResponse> {
        log.error("Job State Inconsistency: {}", e.message)
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "데이터 정합성 오류가 발생했습니다.")
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn("Illegal Argument: {}", e.message)
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.message ?: "잘못된 요청")
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        log.warn("Illegal State: {}", e.message)
        return buildErrorResponse(HttpStatus.CONFLICT, e.message ?: "상태 충돌 발생")
    }

    private fun buildErrorResponse(status: HttpStatus, message: String): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            message = message
        )
        return ResponseEntity.status(status).body(response)
    }
}
