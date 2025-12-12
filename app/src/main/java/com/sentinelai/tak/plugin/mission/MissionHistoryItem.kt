package com.sentinelai.tak.plugin.mission

import com.sentinelai.tak.plugin.network.dto.MissionAnalysisResponseDto
import java.time.ZonedDateTime

data class MissionHistoryItem(
    val question: String,
    val timestamp: ZonedDateTime,
    val response: MissionAnalysisResponseDto? = null,
    val error: String? = null,
)
