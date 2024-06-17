package com.qiangpiao

import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
class ConfigUtility(val env: Environment) {

    fun getProperty(pkey : String) : String? {
        return env.getProperty(pkey)
    }

}