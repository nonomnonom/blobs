package dev.blobs.app.inference

import java.util.Locale

object OfflineAnswerNotice {
    private val whitespace = Regex("\\s+")
    private val liveRequest = Regex(
        "\\b(open (now|right now|today|tonight)|currently (open|available)|" +
            "(current|latest|live|today's) " +
            "(opening hours|hours|reviews?|ratings|prices?|(train |bus |flight )?fares?|" +
            "availability|weather|traffic|exchange rates?)|" +
            "(weather|traffic|prices?|fares?|ratings?|reviews?) (right now|today|tonight))\\b"
    )
    fun forQuestion(question: String): String =
        if (liveRequest.containsMatchIn(whitespace.replace(question.lowercase(Locale.ROOT), " "))) {
            "Offline reference only: I can't verify live information. Treat the details below " +
                "as information from saved sources that may be outdated; individual entries can " +
                "be older than the snapshot date.\n\n"
        } else {
            ""
        }
}
