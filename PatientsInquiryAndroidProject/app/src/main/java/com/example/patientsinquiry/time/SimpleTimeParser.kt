package com.example.patientsinquiry.time

import java.util.Calendar
import java.util.regex.Pattern

object SimpleTimeParser {
    fun parseWhen(raw: String): Long? {
        val lower = raw.lowercase()
        val now = Calendar.getInstance()

        val tomorrow = Pattern.compile("tomorrow(?: at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?)?").matcher(lower)
        if (tomorrow.find()) {
            val hour = tomorrow.group(1)?.toIntOrNull() ?: 9
            val minute = tomorrow.group(2)?.toIntOrNull() ?: 0
            val ampm = tomorrow.group(3) ?: "am"
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            var h = hour
            if (ampm=="pm" && h!=12) h += 12
            if (ampm=="am" && h==12) h = 0
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        val inX = Pattern.compile("in (\\d+) (minute|minutes|hour|hours)").matcher(lower)
        if (inX.find()) {
            val qty = inX.group(1)?.toIntOrNull() ?: return null
            val unit = inX.group(2)!!
            val cal = Calendar.getInstance()
            if (unit.contains("hour")) cal.add(Calendar.HOUR_OF_DAY, qty) else cal.add(Calendar.MINUTE, qty)
            return cal.timeInMillis
        }

        val at = Pattern.compile("at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").matcher(lower)
        if (at.find()) {
            var hour = at.group(1)?.toIntOrNull() ?: return null
            val minute = at.group(2)?.toIntOrNull() ?: 0
            val ampm = at.group(3)
            if (ampm=="pm" && hour!=12) hour += 12
            if (ampm=="am" && hour==12) hour = 0
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        return null
    }
}
