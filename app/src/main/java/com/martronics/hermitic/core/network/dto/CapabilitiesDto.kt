package com.martronics.hermitic.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponseDto(
    val status: String = "unknown",
)

@Serializable
data class AuthCapabilityDto(
    val type: String = "bearer",
    val required: Boolean = true,
)

@Serializable
data class CapabilitiesResponseDto(
    @SerialName("object") val objectType: String = "",
    val platform: String = "",
    val model: String = "",
    val auth: AuthCapabilityDto? = null,
    val features: Map<String, Boolean> = emptyMap(),
    val endpoints: Map<String, String> = emptyMap(),
)
