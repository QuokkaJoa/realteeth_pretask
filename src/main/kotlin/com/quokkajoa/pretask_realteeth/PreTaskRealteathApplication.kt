package com.quokkajoa.pretask_realteeth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PreTaskRealteathApplication

fun main(args: Array<String>) {
    runApplication<PreTaskRealteathApplication>(*args)
}
