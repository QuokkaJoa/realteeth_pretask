package com.quokkajoa.pretask_realteeth.dto

data class ProcessRequest(
        val imageUrl: String
    )

    data class ProcessStartResponse(
        val jobId: String,
        val status: WorkerStatus
    )

    data class ProcessStatusResponse(
        val jobId: String,
        val status: WorkerStatus,
        val result: String? = null
    )
