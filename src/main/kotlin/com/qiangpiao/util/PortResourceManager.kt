package com.qiangpiao.util

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.net.ServerSocket

@Component
class PortResourceManager(environment: Environment) {

    private val portPool = ArrayDeque<Int>().apply {
        var startingPort = environment.getProperty("starting.port.default")?.toInt()?:8081
        val numOfPorts = environment.getProperty("total.num.of.ports")?.toInt()?:4
        do {
            while(!isPortAvailable(startingPort)) {
                startingPort++
            }
            add(startingPort++)
        } while(size < numOfPorts)
    }

    private fun isPortAvailable(port:Int):Boolean {
        return try {
            ServerSocket(port).use {
                serv -> serv.localPort == port
            }
        } catch (e:Exception) {
            false
        }
    }

    fun pollAPort() : Int {
        return try {
            portPool.removeFirst();
        }catch (e:NoSuchElementException) {
            throw Exception("Ports are all used up")
        }
    }



}