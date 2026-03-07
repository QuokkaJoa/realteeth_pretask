package com.quokkajoa.pretask_realteeth.dto

import org.springframework.data.domain.Page

data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
) {
    companion object{
        fun <T> from(page: Page<T>): PagedResponse<T> {
            return PagedResponse(
                content = page.content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                hasNext = page.hasNext()
            )
        }
    }
}
