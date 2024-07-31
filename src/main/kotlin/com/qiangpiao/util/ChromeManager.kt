package com.qiangpiao.util

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.nio.file.Files

@Component
class ChromeManager(val env: Environment, val portResourceManager: PortResourceManager) {
    // dynamically generate web browser
    private fun startChromeBrowser() : Int {
        val curPort = portResourceManager.pollAPort()
        val usrDataDir = createUsrDataTempDir();
        println(usrDataDir)
        val cmd = arrayOf(env.getProperty("chrome_loc"), "--remote-debugging-port=$curPort", "--user-data-dir=${usrDataDir}")
        Runtime.getRuntime().exec(cmd)
        return curPort
    }

    private fun createUsrDataTempDir(): String {
       return Files.createTempDirectory("tempdir").toAbsolutePath().toString();
    }


    fun createChromeDriver() : ChromeDriver? {
        return try{
            var nextFreePort =  startChromeBrowser()
            ChromeDriver(chromeOptions("localhost:$nextFreePort", arrayOf("--start-maximized")))
        } catch (e:Exception) {
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