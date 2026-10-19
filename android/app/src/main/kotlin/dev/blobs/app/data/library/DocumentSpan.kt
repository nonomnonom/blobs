package dev.blobs.app.data.library

private const val PASSAGE_LENGTH = 1400

internal data class DocumentSpan(val startCodePoint: Int, val endCodePoint: Int, val text: String)

internal fun documentSpans(body: String): List<DocumentSpan> {
    val result = mutableListOf<DocumentSpan>()
    val totalCodePoints = body.codePointCount(0, body.length)
    var start = 0
    var startCodePoint = 0
    while (start < body.length) {
        while (start < body.length && body[start].isWhitespace()) {
            start++
            startCodePoint++
        }
        if (start >= body.length) break
        val remaining = totalCodePoints - startCodePoint
        var end = body.offsetByCodePoints(start, minOf(PASSAGE_LENGTH, remaining))
        if (end < body.length) {
            val window = body.substring(start, end)
            val paragraph = window.lastIndexOf("\n\n").takeIf { it >= PASSAGE_LENGTH / 2 }
            val sentence = listOf(". ", "? ", "! ").map { window.lastIndexOf(it) + 1 }
                .filter { it >= PASSAGE_LENGTH / 2 }.maxOrNull()
            val whitespace = maxOf(window.lastIndexOf(' '), window.lastIndexOf('\n'))
            val relative = paragraph?.plus(2) ?: sentence ?: whitespace.takeIf { it > 0 }
            if (relative != null) end = start + relative
        }
        while (end > start && body[end - 1].isWhitespace()) end--
        if (end <= start) end = body.offsetByCodePoints(start, 1)
        val endCodePoint = startCodePoint + body.codePointCount(start, end)
        result.add(DocumentSpan(startCodePoint, endCodePoint, body.substring(start, end)))
        start = end
        startCodePoint = endCodePoint
    }
    return result
}
