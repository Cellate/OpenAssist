package com.openassist.app

data class AssistantHistoryItem(
    val id: String,
    val query: String,
    val serviceId: String,
    val serviceName: String,
    val timestamp: Long,
    val url: String = ""
)
