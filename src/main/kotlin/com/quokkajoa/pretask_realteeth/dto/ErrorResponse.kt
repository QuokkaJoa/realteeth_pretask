package com.quokkajoa.pretask_realteeth.dto

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String
)
