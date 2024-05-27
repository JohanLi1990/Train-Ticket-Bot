package com.qiangpiao

import java.util.*

//https://medium.com/@platky/very-simple-property-reading-in-kotlin-3265cb4382bf
private const val CONFIG = "config.properties"

object PropertiesReader {
    private val properties = Properties()

    init {
        val file = this::class.java.classLoader.getResourceAsStream(CONFIG)
        properties.load(file)
    }

    fun getProperty(key: String): String = properties.getProperty(key)
}
