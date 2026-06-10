package com.patgrady64.sincewhen

import java.util.UUID

data class Moment(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var timestamp: Long, // Changed to 'var' to allow updates
    val isAllDay: Boolean = false
)