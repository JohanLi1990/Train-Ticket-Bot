package com.qiangpiao

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.openqa.selenium.By
import org.openqa.selenium.ElementNotInteractableException
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.Keys
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.Select
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.Duration
import java.util.concurrent.Executors

class GrabTicket(args: Array<String>?) {
    private var mode: String

    private val dispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    init {
        mode = if (!args.isNullOrEmpty()) {
            args[0]
        } else {
            ""
        }
    }

    fun process(): Unit = runBlocking{
        if (mode == "monitor") {
            // start one Chrome browser
            println("launching one browser only...")
            val driver1 = ChromeDriver(chromeOptions("localhost:1234", arrayOf("--start-maximized")))
//            val driver2 = ChromeDriver(chromeOptions("localhost:8989", arrayOf("--start-maximized")))
            supervisorScope {
//                bookOneWay(driver1, "24 Aug 2024", PropertiesReader.getProperty("JBWDL"), true)
                val tripTimes = PropertiesReader.getProperty("JBWDL_RETURN").split(",")
                bookReturn(driver1, "15 Sep 2024", returnDate = "21 Sep 2024",
                    onWardTripTime = tripTimes[0], returnTripTime = tripTimes[1], jBToWdl = true)
//                bookOneWay(driver2, "9 Aug 2024", PropertiesReader.getProperty("WDLJB"))
            }
        } else {
            println("launching three browser...")
            val driver1 = ChromeDriver(chromeOptions("localhost:1234", arrayOf("--start-maximized")))
            val driver2 = ChromeDriver(chromeOptions("localhost:8989", arrayOf("--start-maximized")))
            val driver3 = ChromeDriver(chromeOptions("localhost:7000", arrayOf("--start-maximized")))
            val tripTimes = PropertiesReader.getProperty("JBWDL_RETURN").split(",")

            supervisorScope {
                bookOneWay(driver1, "3 Aug 2024", PropertiesReader.getProperty("WDLJB"))
                bookOneWay(driver2, "10 Nov 2024",PropertiesReader.getProperty("JBWDL"), true)
                bookReturn(driver3, "15 Sep 2024", returnDate = "21 Sep 2024",
                    onWardTripTime = tripTimes[0], returnTripTime = tripTimes[1], jBToWdl = true)
            }
        }
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
                returnDate = "", returnTime = "", jBToWdl = isReturn
            ).decide(State.LOGIN)
        }
    }

    private fun CoroutineScope.bookReturn(driver:ChromeDriver, onWardDate: String,
                           onWardTripTime: String,
                           returnDate:String, returnTripTime:String, jBToWdl: Boolean) {
        launch(CoroutineName(onWardDate) + dispatcher) {
            BookTicketStateMachine(
                driver = driver, onWardDate = onWardDate, returnDate = returnDate,
                onWardTime = onWardTripTime, returnTime = returnTripTime, jBToWdl = jBToWdl
            ).decide(State.LOGIN)
        }
    }

}

enum class State {
    LOGIN, SELECT_DATE, SELECT_TIME, RECAPTCHA,UPDATE_PSG_DETAILS, QUIT
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