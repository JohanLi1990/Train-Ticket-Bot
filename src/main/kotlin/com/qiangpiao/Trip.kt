package com.qiangpiao

data class Trip(val pax:Int,
                val onwardDate:String,
                val onwardTime:String,
                val isJBToWDL:Boolean,
                val Mode:String,
                val returnDate:String?,
                val returnTime:String?
                ) {

    fun isOneWay():Boolean {
        return returnDate.isNullOrEmpty() || returnTime.isNullOrEmpty()
    }
}
