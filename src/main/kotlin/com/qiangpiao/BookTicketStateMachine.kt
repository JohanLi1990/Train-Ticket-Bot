package com.qiangpiao

import kotlinx.coroutines.delay
import org.openqa.selenium.By
import org.openqa.selenium.ElementNotInteractableException
import org.openqa.selenium.NoSuchElementException
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.Select
import org.openqa.selenium.support.ui.WebDriverWait

class BookTicketStateMachine(
    val driver: WebDriver,
    val wait: WebDriverWait, val onWardDate: String,
    val returnDate: String, val onWardTime: String, val returnTime: String,
    val jBToWdl: Boolean = false
) {

    suspend fun decide(state: State) {

        when (state) {
            State.LOGIN -> {
                val res = login()
                delay(500)
                decide(res)
            }

            State.SELECT_DATE -> {
                val res =
            }

            State.SELECT_TIME -> TODO()
            State.UPDATE_PSG_DETAILS -> TODO()
            State.QUIT -> {
                println("service ended...")
            }
        }

    }

    private fun login(): State {
        //TODO how to save login information such that we do not have to
        // manully login every time?
        // https://stackoverflow.com/questions/15058462/how-to-save-and-load-cookies-using-python-selenium-webdriver
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

    private fun selectDateForShuttle() {
        driver.get(PropertiesReader.getProperty("shuttle_url"))
        // need to dismiss modal

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
                when (e) {
                    is NoSuchElementException -> println("element not found... retry")
                    is StaleElementReferenceException -> println("element obsolete/not loaded uet... retry")
                    is ElementNotInteractableException -> println("element not interactable...retry")
                    else -> throw e
                }
                delay(100)
            }
        }

    }

}