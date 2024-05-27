package com.qiangpiao

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

class GrabTicket(args: Array<String>?) {
    private var mode: String

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
        launch (CoroutineName(onwardDate))  {
//            buyTicket(driver1, onwardDate, "", onWardTime = tripTime, jBToWdl = isReturn)

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
        launch(CoroutineName(onWardDate) + Dispatchers.IO) {
//            buyTicket(driver, onWardDate, returnDate, onWardTripTime, returnTripTime, jBToWdl)
            BookTicketStateMachine(
                driver = driver, onWardDate = onWardDate, returnDate = returnDate,
                onWardTime = onWardTripTime, returnTime = returnTripTime, jBToWdl = jBToWdl
            ).decide(State.LOGIN)
        }
    }


    private suspend fun buyTicket(
        driver: WebDriver, onWardDate: String, returnDate: String = "",
        onWardTime: String = "0800", returnTime: String = "",
        jBToWdl: Boolean = false
    ) {
        val wait = WebDriverWait(driver, Duration.ofSeconds(15))

        // initalize state machine:

        login(driver, wait)
        selectDateForShuttle(driver, wait, onWardDate, returnDate, jBToWdl)
        delay(800)
        selectTimeSlot(driver, onWardTime, returnTime)
        delay(700)
        updatePassengerDetails(driver, wait)
        println("driver closing...")
//    driver.quit()
    }

    private fun updatePassengerDetails(driver: WebDriver, wait: WebDriverWait) {
        wait.until(ExpectedConditions.titleIs("Passenger details"))
        println("successfully landed in passenger details page...")
        fillInForSelf(driver)
        fillInForOthers(driver, wait)

    }

    private fun fillInForOthers(driver: WebDriver, wait: WebDriverWait) {
        val num = Integer.parseInt(PropertiesReader.getProperty("pax"))
        if (num == 1) return
        for (i in 1..<num) {
            wait.until {
                val psg = driver.findElement(By.id("Passengers_${i}__FullName"))
                psg.sendKeys(PropertiesReader.getProperty("PASSENGER${i}"))
                psg.sendKeys(Keys.ENTER)
            }
        }
    }

    private fun fillInForSelf(driver: WebDriver) {
        val jsDriver = driver as JavascriptExecutor
        jsDriver.executeScript("document.getElementById('Passengers_0__IsSelf').click()")
    }

    private fun handleCaptcha(driver: WebDriver, wait: WebDriverWait) {
        // to avoid captcha you need to visit once and remember the cookies
//    https://stackoverflow.com/questions/43715178/click-on-captcha-via-selenium-always-raised-picture-verification
        wait.until(
            ExpectedConditions.frameToBeAvailableAndSwitchToIt(
                By.xpath("//iframe[starts-with(@name, 'a-') and starts-with(@src, 'https://www.google.com/recaptcha')]")
            )
        )
        val js = driver as JavascriptExecutor
        js.executeScript("document.getElementsByClassName('recaptcha-checkbox-checkmark')[0].click()")
    }

    private suspend fun selectTimeSlot(driver: WebDriver, onWardTime: String = "0800", returnTime: String = "") {
        //if I can see No trips found
        // refresh page and check again
        val specialWait = WebDriverWait(driver, Duration.ofSeconds(2))
        var count = 2000
        while (count-- >= 0) {
            try {
                waitForPopupToGoAway(driver)

                specialWait.until {
                    val row = driver.findElement(By.cssSelector("tr[data-hourminute='$onWardTime']"))
                    val rowSelect = row.findElement(By.cssSelector("a"))
                    rowSelect.click()
                    handleCaptcha(driver, specialWait)
                    return@until true
                }
                if (returnTime.isNotEmpty()) {
                    specialWait.until {
                        val row = driver.findElement(By.cssSelector("tr[data-hourminute='$returnTime']"))
                        val rowSelect = row.findElement(By.cssSelector("a"))
                        rowSelect.click()
                        return@until true
                    }
                }
                return
            } catch (e: Exception) {
                // cannot select row:
                when(e) {
                    is TimeoutException -> println("time out")
                    is ElementNotInteractableException -> println("not enabled yet")
                    else -> throw e
                }
                driver.navigate().refresh()
                delay(2200)
            }
        }

        if (count < 0) throw NoSuchElementException("Ticket not available")
        // if there n
    }

    private suspend fun waitForPopupToGoAway(driver: WebDriver) {
        while (true) {
            try {
                val spin = driver.findElement(By.id("popupModal"))
                if (spin.getAttribute("style").contains("display: none;")) {
                    return
                } else {
                    val closeBtn = spin.findElement(By.id("popupModalCloseButton"))
                    closeBtn.click()
                }
            } catch (e: Exception) {
                when(e) {
                    is NoSuchElementException -> println("element not found... retry")
                    is StaleElementReferenceException -> println("element obsolete/not loaded uet... retry")
                    is ElementNotInteractableException -> println("element not interactable...retry")
                    else -> throw e
                }
                delay(100)
            }
        }

    }


    private fun selectDateForShuttle(
        driver: WebDriver, wait: WebDriverWait, onWard: String,
        returnDate: String = "",
        jBToWdl: Boolean = false
    ) {
        driver.get("https://shuttleonline.ktmb.com.my/Home/Shuttle")
        Thread.sleep(2000)
        if (!jBToWdl) {
            // reverse direction
            val reverse = driver.findElement(By.cssSelector("i[onclick='SwapFromToTerminal()']"))
            wait.until {
                reverse.isDisplayed
            }
            reverse.click()
        }


        wait.until {
            val pax = driver.findElement(By.id("PassengerCount"))
            val numOfPeople = PropertiesReader.getProperty("pax")
            Select(pax).apply {
                selectByValue(numOfPeople)
            }
            return@until true
        }

        // set departure date
        setDepartureDate(driver, wait, onWard)
        if (returnDate.isNotEmpty()) {
            setReturnDate(driver, wait, returnDate)
        }
        wait.until {
            driver.findElement(By.id("btnSubmit")).click()
            return@until true
        }
    }

    private fun setReturnDate(driver: WebDriver, wait: WebDriverWait, returnDate: String) {
        wait.until {
            driver.findElement(By.id("ReturnDate"))
            return@until true
        }
        val js = driver as JavascriptExecutor
        js.executeScript("document.getElementById('ReturnDate').value='$returnDate'")
    }

    private fun setDepartureDate(driver: WebDriver, wait: WebDriverWait, date: String) {
        // TODO if not specified default to the weekend 6 months later
        wait.until {
            driver.findElement(By.id("OnwardDate"))
            return@until true
        }
        val js = driver as JavascriptExecutor
        js.executeScript("document.getElementById('OnwardDate').value='$date'")

    }

    private fun dismissBanner(driver: WebDriver, wait: WebDriverWait) {
        var announcement: WebElement? = null
        wait.until {
            announcement = driver.findElement(By.id("announcement-div"))
            announcement?.isEnabled
        }
        announcement ?: return
        var annBtn: WebElement? = null
        wait.until {
            annBtn = announcement?.findElement(By.cssSelector("button[type='button']"))
            annBtn?.isEnabled
        }

        annBtn?.click()


    }

    private fun login(driver: WebDriver, wait: WebDriverWait) {
        //TODO how to save login information such that we do not have to
        // manully login every time?
        // https://stackoverflow.com/questions/15058462/how-to-save-and-load-cookies-using-python-selenium-webdriver
        driver.get("https://online.ktmb.com.my/Account/Login")
        wait.until {
            driver.title.isNotBlank()
            println(driver.title)
        }
        if (!driver.title.equals("Login")) return

        wait.until {
            val pword = driver.findElement(By.id("Password"))
            val email = driver.findElement(By.id("Email"))
            pword.sendKeys(PropertiesReader.getProperty("password"))
            email.sendKeys(PropertiesReader.getProperty("email"))
            driver.findElement(By.id("LoginButton")).click()
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