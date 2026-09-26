package org.adhkaar.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    private val device = DeviceReport("Tecno KI5k", "13 (API 33)", "0.1.0 (1)", "Lockdown", "ha-NG")

    private fun parse(body: String) = Json.parseToJsonElement(body).jsonObject.mapValues { it.value.jsonPrimitive.content }

    @Test
    fun bodyHasEveryFieldTheWorkerReads() {
        val body = parse(Feedback.json(FeedbackTopic.CORRECTION, "  The translation of \"SubhanAllah\" is off.\n", " 0803 000 0000 ", device))
        assertEquals(
            mapOf(
                "topic" to "correction",
                "message" to "The translation of \"SubhanAllah\" is off.",
                "contact" to "0803 000 0000",
                "device" to "Tecno KI5k",
                "android" to "13 (API 33)",
                "appVersion" to "0.1.0 (1)",
                "mode" to "Lockdown",
                "language" to "ha-NG",
            ),
            body,
        )
    }

    @Test
    fun blankContactIsSentEmpty() {
        assertEquals("", parse(Feedback.json(FeedbackTopic.OTHER, "Just saying salaam", "   ", device))["contact"])
    }

    @Test
    fun messageIsCappedAtTheWorkerLimit() {
        val body = parse(Feedback.json(FeedbackTopic.PROBLEM, "a".repeat(6000), "", device))
        assertEquals(Feedback.MAX_MESSAGE, body.getValue("message").length)
    }

    @Test
    fun needsTenCharactersBeyondWhitespace() {
        assertFalse(Feedback.canSend("   short   "))
        assertTrue(Feedback.canSend("It crashed"))
    }

    @Test
    fun topicIdsMatchTheWorker() {
        assertEquals(listOf("problem", "suggestion", "correction", "other"), FeedbackTopic.entries.map { it.id })
    }
}
