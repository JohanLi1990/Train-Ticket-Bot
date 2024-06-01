package com.qiangpiao

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.util.concurrent.Executors

class GrabTicket(args: Array<String>?) {
    private var tempOperatingMode: String = if (!args.isNullOrEmpty()) {
        args[0]
    } else {
        ""
    }
    private val dispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
//    private val dispatcher = Dispatchers.Default

    fun process(): Unit = runBlocking{
        if (tempOperatingMode == "one") {
            // start one Chrome browser
            println("launching one browser only...")
            val driver1 = ChromeDriver(chromeOptions("localhost:1234", arrayOf("--start-maximized")))
//            val driver2 = ChromeDriver(chromeOptions("localhost:8989", arrayOf("--start-maximized")))
            supervisorScope {
//                bookOneWay(driver1, "24 Aug 2024", PropertiesReader.getProperty("JBWDL"), true)
//                val tripTimes = PropertiesReader.getProperty("JBWDL_RETURN").split(",")
                bookOneWay(driver1, "20 Oct 2024", tripTime="2015", isReturn = true)
//                bookOneWay(driver2, "9 Aug 2024", PropertiesReader.getProperty("WDLJB"))
            }
        } else {
            println("launching three browser...")
            val driver1 = ChromeDriver(chromeOptions("localhost:1234", arrayOf("--start-maximized")))
            val driver2 = ChromeDriver(chromeOptions("localhost:8989", arrayOf("--start-maximized")))
            val driver3 = ChromeDriver(chromeOptions("localhost:7000", arrayOf("--start-maximized")))

            supervisorScope {
                bookReturn(driver1, "3 Nov 2024", "2015",
                    returnDate = "9 Nov 2024", returnTripTime = "0830", jBToWdl = true)
                bookReturn(driver2, "10 Nov 2024", returnDate = "23 Nov 2024",
                    onWardTripTime = "2015", returnTripTime = "0830", jBToWdl = true)
                bookOneWay(driver3, "17 Nov 2024","2015", true)

            }
        }
        dispatcher.close()
    }

    private fun chromeOptions(host:String, args:Array<String>): ChromeOptions {
        val options = ChromeOptions()
        options.setExperimentalOption("debuggerAddress", host)
        args.forEach {
            options.addArguments(it)
        }
        return options
    }

    private fun CoroutineScope.bookOneWay(driver1: ChromeDriver,
                           onwardDate:String,
                           tripTime: String, isReturn:Boolean=false) {
        launch (CoroutineName(onwardDate) + dispatcher)  {
            BookTicketStateMachine(
                driver = driver1, onWardDate = onwardDate,
                onWardTime =tripTime,
                returnDate = "", returnTime = "", jBToWdl = isReturn, mode = Mode.HUSTLE
            ).runStateMachine()
        }
    }

    private fun CoroutineScope.bookReturn(driver:ChromeDriver, onWardDate: String,
                           onWardTripTime: String,
                           returnDate:String, returnTripTime:String, jBToWdl: Boolean) {
        launch(CoroutineName(onWardDate) + dispatcher) {
            BookTicketStateMachine(
                driver = driver, onWardDate = onWardDate, returnDate = returnDate,
                onWardTime = onWardTripTime, returnTime = returnTripTime, jBToWdl = jBToWdl
            ).runStateMachine()
        }
    }

}

enum class Mode {
    HUSTLE, MONITOR
}



fun main(args: Array<String>) {
//    https://www.selenium.dev/documentation/webdriver/getting_started/first_script/
//   https://juejin.cn/post/7008718256752033799

    // when you have time , learn to use corouting chanel
    // https://stackoverflow.com/questions/73119442/how-to-properly-have-a-queue-of-pending-operations-using-kotlin-coroutines/73125591#73125591

    /**
     * We need to run the following command first
     * .\chrome.exe --remote-debugging-port=1234 --user-data-dir=D:/usrData2
     * .\chrome.exe --remote-debugging-port=8989 --user-data-dir=D:/usrData
     * .\chrome.exe --remote-debugging-port=7000 --user-data-dir=D:/usrData3
     */

    val app = GrabTicket(args)
    app.process()

}