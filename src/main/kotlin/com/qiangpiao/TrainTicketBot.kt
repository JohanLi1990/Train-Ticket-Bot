package com.qiangpiao

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.util.concurrent.Executors


@SpringBootApplication
class TrainTicketBot{

    @Bean
    fun dispatcher() : ExecutorCoroutineDispatcher{
        return Executors.newFixedThreadPool(3).asCoroutineDispatcher()
    }
}

fun main(args: Array<String>) {
    runApplication<TrainTicketBot>(*args)
}

