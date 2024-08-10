package com.qiangpiao.util

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
class ChromeManager(val env: Environment, val portResourceManager: PortResourceManager) {
    // dynamically generate web browser

    private val tempFileList = ArrayList<Path>()

    private fun startChromeBrowser(): Int {
        val curPort = portResourceManager.pollAPort()
        val usrDataDir = createUsrDataTempDir()
        val cmd =
            arrayOf(env.getProperty("chrome_loc"), "--remote-debugging-port=$curPort", "--user-data-dir=${usrDataDir}")
        Runtime.getRuntime().exec(cmd)
        return curPort
    }

    private fun createUsrDataTempDir(): String {
        val tempdir = Files.createTempDirectory("ttbdir")
        // add shutdown hook here.
        tempFileList.add(tempdir)
        return tempdir.toAbsolutePath().toString()
//        Files.createTempDirectory("tempdir").toAbsolutePath().toString();
    }


    fun createChromeDriver(): ChromeDriver? {
        return try {
            var nextFreePort = startChromeBrowser()
            ChromeDriver(chromeOptions("localhost:$nextFreePort", arrayOf("--start-maximized")))
        } catch (e: Exception) {
            null
        }
    }

    private fun chromeOptions(host: String, args: Array<String>): ChromeOptions {
        val options = ChromeOptions()
        options.setExperimentalOption("debuggerAddress", host)
        args.forEach {
            options.addArguments(it)
        }
        return options
    }

}