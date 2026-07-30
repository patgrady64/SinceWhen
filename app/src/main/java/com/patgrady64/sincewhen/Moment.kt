package com.patgrady64.sincewhen

import java.util.UUID

data class Moment(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var timestamp: Long,
    val isAllDay: Boolean = false,
    var calendarEventId: Long? = null,
    var calendarId: Long? = null
)
