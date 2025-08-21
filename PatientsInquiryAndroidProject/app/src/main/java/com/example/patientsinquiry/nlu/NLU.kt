package com.example.patientsinquiry.nlu

import kotlin.random.Random

data class IntentResult(val type: String, val args: Map<String, String> = emptyMap())

fun parseIntent(text: String): IntentResult {
    val t = text.lowercase().trim()

    if (listOf("help me", "not sure", "how to say", "prompt", "phrase it").any { t.contains(it) }) {
        return IntentResult("prompt_help")
    }
    if (listOf("what did i ask", "find my answers", "show entries", "search").any { t.contains(it) }) {
        val topic = Regex("(about|for)\\s+([a-z0-9\\s]+)").find(t)?.groupValues?.getOrNull(2)?.trim() ?: t
        return IntentResult("search", mapOf("query" to topic))
    }
    if (listOf("remind me", "set a reminder", "follow up", "follow-up").any { t.contains(it) }) {
        return IntentResult("set_reminder", mapOf("raw" to t))
    }
    if (listOf("list reminders", "what are my reminders").any { t.contains(it) }) {
        return IntentResult("list_reminders")
    }
    if (listOf("delete reminder", "remove reminder", "mark reminder").any { t.contains(it) }) {
        val id = Regex("(\\d+)").find(t)?.groupValues?.getOrNull(1) ?: ""
        return IntentResult("done_reminder", mapOf("id" to id))
    }
    if (listOf("i'm feeling better", "i feel better", "celebrate", "play my favorite").any { t.contains(it) }) {
        return IntentResult("celebrate")
    }
    if (listOf("set media folder").any { t.contains(it) }) {
        return IntentResult("set_media_folder")
    }
    if (listOf("i feel", "i have", "symptom", "pain", "fatigue", "tired", "headache", "nausea", "cough", "fever", "mood").any { t.contains(it) }) {
        return IntentResult("symptom_report")
    }
    if (listOf("what can you do", "help", "commands").any { t.contains(it) }) {
        return IntentResult("help")
    }
    return IntentResult("unknown")
}

object PromptHelper {
    private val tips = listOf(
        "You can say: 'I feel [symptom], starting [timeframe], severity [1-10].'",
        "Try: 'Remind me [time] to check my symptoms.'",
        "Ask: 'What did I ask about [topic] last week?'"
    )
    fun randomTip() = tips[Random.nextInt(tips.size)]
}

object SuggestionEngine {
    fun answerFor(text: String): String {
        val t = text.lowercase()
        return when {
            t.contains("fatigue") || t.contains("tired") -> "Consider rest, hydration, and checking sleep quality. If fatigue persists or is severe, consult a clinician. See Care tab for links."
            t.contains("fever") -> "Monitor temperature, hydrate, and consider acetaminophen if appropriate. Seek care for high or persistent fever."
            t.contains("headache") -> "Try hydration, rest, and reducing screen time. Seek care if sudden, severe, or with other symptoms."
            else -> "I don't have a definitive answer. Check Care links for trustworthy guidance or contact a clinician."
        }
    }
}
