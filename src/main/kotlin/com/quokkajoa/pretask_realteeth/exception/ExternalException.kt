package com.quokkajoa.pretask_realteeth.exception

sealed class ExternalException(message: String) : RuntimeException(message)

class ExternalClientException(val statusCode: Int, message: String)
    : ExternalException("[$statusCode] $message")

class ExternalServiceException(val statusCode: Int, message: String)
    : ExternalException("[$statusCode] $message")

class ExternalRateLimitException(val statusCode: Int, message: String)
    : ExternalException("[$statusCode] $message")
