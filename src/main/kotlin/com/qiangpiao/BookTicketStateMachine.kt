package com.qiangpiao

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
import java.time.Duration

class BookTicketStateMachine(
    val driver: WebDriver,
    val onWardDate: String,
    val returnDate: String,
    val onWardTime: String = "0800",
    val returnTime: String,
    val jBToWdl: Boolean = false
) {
    private val wait: WebDriverWait = WebDriverWait(driver, Duration.ofSeconds(15))

    suspend fun decide(state: State) {

        when (state) {
            State.LOGIN -> {
                val res = login()
                delay(500)
                decide(res)
            }

            State.SELECT_DATE -> {
                val res = selectDateForShuttle()
                delay(300)
                decide(res)
            }

            State.SELECT_TIME -> {
                val res = selectTimeSlot()
                delay(1500)
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

            State.QUIT -> {
                println("${Thread.currentThread().name}: service ended...")
            }
        }
    }

    private fun updatePassengerDetails(): State {
        return try {
            wait.until(ExpectedConditions.titleIs("Passenger details"))
            println("successfully landed in passenger details page...")
            fillInForSelf()
            fillInForOthers()
            State.QUIT
        } catch (e: Exception) {
            // something wrong
            driver.navigate().refresh()
            println("Update not working")
            State.UPDATE_PSG_DETAILS
        }

    }

    private fun fillInForOthers() {
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

    private fun fillInForSelf() {
        val jsDriver = driver as JavascriptExecutor
        jsDriver.executeScript("document.getElementById('Passengers_0__IsSelf').click()")
    }

    private fun login(): State {
        driver.get(PropertiesReader.getProperty("login_url"))
        try {
            wait.until {
                driver.title.isNotBlank()
                println(driver.title)
                return@until true
            }
            if (!driver.title.equals("Login")) return State.SELECT_DATE

            wait.until {
                val pWord = driver.findElement(By.id("Password"))
                val email = driver.findElement(By.id("Email"))
                pWord.sendKeys(PropertiesReader.getProperty("password"))
                email.sendKeys(PropertiesReader.getProperty("email"))
                driver.findElement(By.id("LoginButton")).click()
                return@until true
            }
            return State.SELECT_DATE
        } catch (e: Exception) {
            println("exception: ${e.message}")
            // retry
            return State.LOGIN
        }

    }

    private fun selectDateForShuttle(): State {
        driver.get(PropertiesReader.getProperty("shuttle_url"))
        // need to dismiss modal
        if (popupModalIsPresent()) {
            println("Presence of popup modal detected...retry")
            return State.SELECT_DATE
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
                val numOfPeople = PropertiesReader.getProperty("pax")
                Select(pax).apply {
                    selectByValue(numOfPeople)
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
            println(e.message)
            return State.LOGIN
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
            driver.navigate().refresh()
            State.SELECT_DATE
        }
    }

    private fun selectTimeSlot(): State {
        // if Modal is present -> retry
        // if it is sold out or not present yet -> retry
        val specialWait = WebDriverWait(driver, Duration.ofSeconds(2))
        try {
            if (popupModalIsPresent()) {
                driver.navigate().refresh()
                return State.SELECT_TIME
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
            // cannot select row:
            when (e) {
                is TimeoutException -> println("${Thread.currentThread().name}: time out")
                is ElementNotInteractableException -> println("${Thread.currentThread().name}:not enabled yet")
                else -> throw e
            }
            driver.navigate().refresh()
            return State.SELECT_DATE
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
        // TODO if not specified default to the weekend 6 months later
        wait.until {
            driver.findElement(By.id("OnwardDate"))
            return@until true
        }
        val js = driver as JavascriptExecutor
        js.executeScript("document.getElementById('OnwardDate').value='$onWardDate'")

    }

}