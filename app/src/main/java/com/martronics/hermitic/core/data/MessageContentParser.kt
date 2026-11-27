package com.martronics.hermitic.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object MessageContentParser {

    fun toDisplayText(content: JsonElement?): String {
        if (content == null) return ""
        return when (content) {
            is JsonPrimitive -> content.contentOrNull.orEmpty()
            is JsonArray -> content.mapNotNull { part ->
                when (part) {
                    is JsonPrimitive -> part.contentOrNull
                    is JsonObject -> {
                        val type = (part["type"] as? JsonPrimitive)?.contentOrNull
                        when (type) {
                            "text", "input_text", "output_text" ->
                                (part["text"] as? JsonPrimitive)?.contentOrNull
                            "image_url", "input_image", "image" -> "[image]"
                            else -> (part["text"] as? JsonPrimitive)?.contentOrNull
                                ?: (part["content"] as? JsonPrimitive)?.contentOrNull
                        }
                    }
                    else -> null
                }
            }.filter { it.isNotBlank() }.joinToString("\n")
            is JsonObject -> {
                (content["text"] as? JsonPrimitive)?.contentOrNull
                    ?: (content["content"] as? JsonPrimitive)?.contentOrNull
                    ?: content.toString()
            }
        }
    }
}
