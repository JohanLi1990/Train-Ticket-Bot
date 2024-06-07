package com.qiangpiao

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.util.concurrent.Executors

class GrabTicket {
    private val dispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
    companion object{
        val logger = KotlinLogging.logger{}
    }
    fun process(trips: List<Trip>) : Unit = runBlocking {
        supervisorScope {
            for ((i, trip) in trips.withIndex()) {
                logger.info { "trip-$i:${trip}" }
                val driver =  ChromeDriver(chromeOptions(PropertiesReader.getProperty("host_$i"), arrayOf("--start-maximized")))
                if (trip.isOneWay()) {
                    bookOneWay(driver, trip.onwardDate, trip.onwardTime,
                        trip.isJBToWDL, trip.pax, Mode.getMode(trip.Mode))
                } else{
                    bookReturn(driver, trip.onwardDate, trip.onwardTime,
                        trip.returnDate!!, trip.returnTime!!, trip.isJBToWDL, trip.pax,
                        Mode.getMode(trip.Mode))
                }
            }
        }
        dispatcher.close()
    }


    private fun chromeOptions(host: String, args: Array<String>): ChromeOptions {
        val options = ChromeOptions()
        options.setExperimentalOption("debuggerAddress", host)
        args.forEach {
            options.addArguments(it)
        }
        return options
    }

    private fun CoroutineScope.bookOneWay(
        driver1: ChromeDriver,
        onwardDate: String,
        tripTime: String, isReturn: Boolean = false, pax:Int, inputMode:Mode=Mode.HUSTLE
    ) {
        launch(CoroutineName(onwardDate) + dispatcher) {
            BookTicketStateMachine(
                driver = driver1, onWardDate = onwardDate,
                onWardTime = tripTime,
                returnDate = "", returnTime = "", jBToWdl = isReturn, mode = inputMode, numOfPassenger = pax
            ).runStateMachine()
        }
    }

    private fun CoroutineScope.bookReturn(
        driver: ChromeDriver, onWardDate: String,
        onWardTripTime: String,
        returnDate: String, returnTripTime: String, jBToWdl: Boolean, pax:Int,
        inputMode: Mode = Mode.HUSTLE
    ) {
        launch(CoroutineName(onWardDate) + dispatcher) {
            BookTicketStateMachine(
                driver = driver, onWardDate = onWardDate, returnDate = returnDate,
                onWardTime = onWardTripTime, returnTime = returnTripTime, jBToWdl = jBToWdl,
                numOfPassenger = pax, mode = inputMode
            ).runStateMachine()
        }
    }

}

enum class Mode {
    HUSTLE , MONITOR;
    companion object{
        fun getMode(s : String) : Mode {
            return if (s == "H") {
                HUSTLE
            } else {
                MONITOR
            }
        }
    }

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
    val options = Options().apply {
        addOption(Option("help", "print this message"))
        addOption(
            Option.builder("trips").argName("Trip Information")
                .hasArgs().desc("7 fields could be defined, the first 5 are optional: \n" +
                        "<Pax, Onward Date, Onward Time,isJBToWDL,Mode,Return Date, Return Time>. \n" +
                        "For Pax, it could be up to 6; For Date input, use format like 24 Nov 2024, \n" +
                        "For time input, use format like 2015 (8:15pm) or 0830, based on KTMB schedule.\n" +
                        "For isJBToWDL, it is either y or n\n" +
                        "For mode, it is either H or M\n"+
                        "Return Date and Return time are optional, \n" +
                        " if any is empty, we will treat it as a one way trip.\n" +
                        "Currently we support simultaneous booking of 3 trips"
                ).build()
        )
    }

    val parser = DefaultParser()
    val line = parser.parse(options, args)
    if (line.hasOption("help")) {
        HelpFormatter().printHelp("GrabTicket", options)
        return
    }

    if (line.hasOption("trips")) {
        val ans = line.getOptionValues("trips")
        val trips = ArrayList<Trip>().apply {
            ans.forEach {
                val tripDetails = it.split(",")
                add(Trip(tripDetails[0].trim().toInt(), tripDetails[1].trim(),
                    tripDetails[2].trim(),tripDetails[3].trim() == "y",
                    tripDetails[4].trim(),
                    tripDetails.getOrNull(5)?.trim(), tripDetails.getOrNull(5)?.trim()))
            }
        }
        GrabTicket().process(trips)
    }
}

