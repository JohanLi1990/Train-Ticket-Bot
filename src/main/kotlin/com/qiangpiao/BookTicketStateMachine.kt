package com.qiangpiao

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import org.openqa.selenium.By
import org.openqa.selenium.ElementNotInteractableException
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.Keys
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.Select
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.core.env.Environment
import java.time.Duration

class BookTicketStateMachine(
    val driver: WebDriver,
    val onWardDate: String,
    val returnDate: String,
    val onWardTime: String = "0800",
    val returnTime: String,
    val jBToWdl: Boolean = false,
    val mode: Mode = Mode.HUSTLE,
    val numOfPassenger: Int = 1,
    val env: Environment
) {
    private val wait: WebDriverWait = WebDriverWait(driver, Duration.ofSeconds(20))
    private var countDown = if (mode == Mode.MONITOR) 2000 else 100

    private val loginUrl = env.getProperty("login_url")
    private val shuttleUrl = env.getProperty("shuttle_url")

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    suspend fun runStateMachine() {
        logger.info { "${Thread.currentThread().name}: starts...." }
        decide(State.LOGIN)
    }

    private suspend fun decide(state: State) {
        if (countDown == 0) {
            logger.info { "count-down reached... terminating" }
            return
        }
        when (state) {
            State.LOGIN -> {
                val res = login()
                delay(300)
                decide(res)
            }

            State.SELECT_DATE -> {
                val res = selectDateForShuttle()
                delay(300)
                decide(res)
            }

            State.SELECT_TIME -> {
                val res = selectTimeSlot()
                delay(600)
                decide(res)

            }

            State.RECAPTCHA -> {
                val res = handleCaptcha()
                delay(200)
                decide(res)
            }

            State.UPDATE_PSG_DETAILS -> {
                val res = updatePassengerDetails()
                delay(200)
                decide(res)
            }

            State.CHOOSE_PAYMENT_TYPE -> {
                val res = selectPaymentMethod()
                delay(200)
                decide(res)
            }

            State.QUIT -> {
                logger.info { "${Thread.currentThread().name}: state machine ended..." }
            }


        }
    }

    private fun selectPaymentMethod(): State {
        println(driver.title)
        wait.until{
            driver.findElement(By.id("btnKtmbEWallet")).click()
            return@until "completed"
        }

        return State.QUIT

    }

    private fun updatePassengerDetails(): State = try {

        wait.until(ExpectedConditions.titleIs("Passenger details"))
        logger.info { "${Thread.currentThread().name} successfully landed in passenger details page..." }
//        fillInForSelf()
        fillInForOthers()
        proceedToPayment()
    } catch (e: Exception) {
        // something wrong
        logger.info { "Something is wrong at Update Passengers page..cannot proceed to payment.. do not go away" }
        State.QUIT
    }

    private fun proceedToPayment(): State {
        driver.findElement(By.id("btnConfirmPayment")).click()
        return State.CHOOSE_PAYMENT_TYPE
    }


    private fun fillInForOthers() {
//        if (numOfPassenger == 1) return
        for (i in 0..<numOfPassenger) {
            wait.until {
                val psg = driver.findElement(By.id("Passengers_${i}__FullName"))
                psg.sendKeys(env.getProperty("PASSENGER${i}"))
                psg.sendKeys(Keys.ENTER)
                setTicketType(i)
                return@until "completed"
            }
        }
    }

    private fun fillInForSelf() {
        val jsDriver = driver as JavascriptExecutor
        jsDriver.executeScript("document.getElementById('Passengers_0__IsSelf').click()")
        setTicketType(0)
    }

    private fun setTicketType(passenger: Int) {
        // DEWASA ADULT ticket type
        val ticketTypeElement = driver.findElement(By.id("Passengers_${passenger}__TicketTypeId"))
        Select(ticketTypeElement).apply {
            selectByValue("Adult")
        }

    }

    private fun login(): State {

        driver.get(loginUrl)
        try {
            wait.until {
                driver.title.isNotBlank()
                return@until true
            }
            if (!driver.title.equals("Login")) return State.SELECT_DATE

            wait.until {
                val pWord = driver.findElement(By.id("Password"))
                val email = driver.findElement(By.id("Email"))
                pWord.sendKeys(env.getProperty("password"))
                email.sendKeys(env.getProperty("email"))
                driver.findElement(By.id("LoginButton")).click()
                return@until true
            }
            return State.SELECT_DATE
        } catch (e: Exception) {
            logger.info { "exception: ${e.message}" }
            // retry
            countDown--
            return State.LOGIN
        }

    }

    private fun selectDateForShuttle(): State {
        driver.get(shuttleUrl)
        // need to dismiss modal
        if (popupModalIsPresent()) {
            logger.info { "Presence of popup modal detected...retry" }
            return if (mode == Mode.HUSTLE) State.SELECT_DATE else State.LOGIN
        }
        try {
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
                Select(pax).apply {
                    selectByValue(numOfPassenger.toString())
                }
                return@until true
            }
            setDepartureDate()
            if (returnDate.isNotEmpty()) {
                setReturnDate()
            }
            wait.until {
                driver.findElement(By.id("btnSubmit")).click()
                return@until true
            }
            return State.SELECT_TIME
        } catch (e: Exception) {
            logger.info { "retry from select date..." }
            countDown--
            return if (mode == Mode.HUSTLE) State.SELECT_DATE else State.LOGIN
        }
    }

    private fun popupModalIsPresent(): Boolean {
        try {
            val specialWait = WebDriverWait(driver, Duration.ofSeconds(2))
            val style = specialWait.until {
                val spin = driver.findElement(By.id("popupModal"))
                return@until spin.getAttribute("style")
            }
            return style.isNotEmpty() && style.contains("display: none;")
        } catch (e: Exception) {
            return true
        }
    }


    private fun handleCaptcha(): State {
        // to avoid captcha you need to visit once and remember the cookies
//    https://stackoverflow.com/questions/43715178/click-on-captcha-via-selenium-always-raised-picture-verification
        return try {
            wait.until(
                ExpectedConditions.frameToBeAvailableAndSwitchToIt(
                    By.xpath("//iframe[starts-with(@name, 'a-') and starts-with(@src, 'https://www.google.com/recaptcha')]")
                )
            )
            val js = driver as JavascriptExecutor
            js.executeScript("document.getElementsByClassName('recaptcha-checkbox-checkmark')[0].click()")
            State.UPDATE_PSG_DETAILS
        } catch (e: Exception) {
            countDown--
            if (mode == Mode.HUSTLE) {
                State.SELECT_DATE
            } else State.LOGIN
        }
    }

    private fun selectTimeSlot(): State {
        val specialWait = WebDriverWait(driver, Duration.ofSeconds(2))
        try {
            if (popupModalIsPresent()) {
                countDown--
                return if (mode == Mode.HUSTLE) State.SELECT_DATE else State.LOGIN
            }

            specialWait.until {
                val row = driver.findElement(By.cssSelector("tr[data-hourminute='$onWardTime']"))
                val rowSelect = row.findElement(By.cssSelector("a"))
                rowSelect.click()
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
            return State.RECAPTCHA
        } catch (e: Exception) {
            when (e) {
                is TimeoutException -> logger.info { "${Thread.currentThread().name}: time out" }
                is ElementNotInteractableException -> logger.info { "${Thread.currentThread().name}:not enabled yet" }
                else -> throw e
            }
            countDown--
            return if (mode == Mode.HUSTLE) State.SELECT_DATE else State.LOGIN
        }

    }

    private fun setReturnDate() {
        wait.until {
            driver.findElement(By.id("ReturnDate"))
            return@until true
        }
        val js = driver as JavascriptExecutor
        js.executeScript("document.getElementById('ReturnDate').value='$returnDate'")
    }

    private fun setDepartureDate() {
        wait.until {
            driver.findElement(By.id("OnwardDate"))
            return@until true
        }
        val js = driver as JavascriptExecutor
        js.executeScript("document.getElementById('OnwardDate').value='$onWardDate'")
    }

    private enum class State {
        LOGIN, SELECT_DATE, SELECT_TIME, RECAPTCHA, UPDATE_PSG_DETAILS, CHOOSE_PAYMENT_TYPE, QUIT
    }

}