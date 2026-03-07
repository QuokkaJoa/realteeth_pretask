package com.quokkajoa.pretask_realteeth.controller

import com.quokkajoa.pretask_realteeth.dto.JobResponse
import com.quokkajoa.pretask_realteeth.dto.JobSearchParams
import com.quokkajoa.pretask_realteeth.dto.JobSubmitRequest
import com.quokkajoa.pretask_realteeth.dto.PagedResponse
import com.quokkajoa.pretask_realteeth.service.JobService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/jobs")
@Validated
class JobController(
    private val jobService: JobService
) {
    @PostMapping
    fun submitJob(@RequestBody @Valid request: JobSubmitRequest): ResponseEntity<JobResponse> {
        val response = jobService.submitJob(request.idempotencyKey, request.imageUrl)
        return ResponseEntity.status(HttpStatus.ACCEPTED).location(URI.create("/jobs/${response.id}")).body(response)
    }

    @GetMapping("/{id}")
    fun getJob(@PathVariable id: Long): ResponseEntity<JobResponse> {
        val response = jobService.getJob(id)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun getJobs(@ModelAttribute @Valid params: JobSearchParams): ResponseEntity<PagedResponse<JobResponse>> {
        val response = jobService.findJobs(params)
        return ResponseEntity.ok(response)
    }
}
