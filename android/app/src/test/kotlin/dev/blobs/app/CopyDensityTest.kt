package dev.blobs.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The copy budget is a product rule, not a style preference: a surface shows a
 * state line, a number, and one control, and puts the explanation behind a
 * disclosure. This test is the rule's only enforcement point.
 */
class CopyDensityTest {
    private val strings: Map<String, String> = Strings.read()

    @Test
    fun `no shipped string is a paragraph`() {
        val offenders = strings.entries
            .filter { words(it.value) > MAX_STRING_WORDS }
            .sortedByDescending { words(it.value) }
            .map { "${it.key}: ${words(it.value)} words" }
        assertTrue(
            "strings over $MAX_STRING_WORDS words:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    @Test
    fun `headings stay scannable`() {
        val offenders = mutableListOf<String>()
        sourceFiles().forEach { file ->
            callArguments(file.readText(), "Heading").forEach { args ->
                Strings.refs(args).forEach { ref ->
                    val count = words(strings[ref] ?: return@forEach)
                    if (count > MAX_HEADING_WORDS) offenders += "${file.name}: $ref ($count words)"
                }
            }
        }
        assertTrue(
            "headings over $MAX_HEADING_WORDS words:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    @Test
    fun `primary labels are verbs`() {
        val offenders = mutableListOf<String>()
        sourceFiles().forEach { file ->
            val text = file.readText()
            callArguments(text, "PrimaryAction").forEach { args ->
                Strings.refs(args).forEach { ref ->
                    val count = words(strings[ref] ?: return@forEach)
                    if (count > MAX_LABEL_WORDS) offenders += "${file.name}: $ref ($count words)"
                }
            }
            Strings.glyphLabels(text).forEach { ref ->
                val count = words(strings[ref] ?: return@forEach)
                if (count > MAX_LABEL_WORDS) offenders += "${file.name}: $ref ($count words)"
            }
        }
        assertTrue(
            "labels over $MAX_LABEL_WORDS words:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    @Test
    fun `every surface keeps the explanation off the first screen`() {
        val offenders = mutableListOf<String>()
        sourceFiles().forEach { file ->
            val source = file.readText()
            val controls = CONTROL.findAll(source).count()
            if (controls == 0) return@forEach
            val rendered = source.replace(SET_OF, "").replace(COMPARISON, "")
            val words = Strings.refs(rendered)
                .filterNot { it.endsWith("_detail") }
                .sumOf { words(strings[it] ?: "") }
            val ratio = words.toDouble() / controls
            if (ratio > MAX_WORDS_PER_CONTROL) {
                val detail = "$words words / $controls controls = ${"%.1f".format(ratio)}"
                offenders += "${file.name}: $detail"
            }
        }
        val report = offenders.joinToString("\n")
        assertTrue(
            "surfaces over $MAX_WORDS_PER_CONTROL words per control:\n$report",
            offenders.isEmpty()
        )
    }

    private fun sourceFiles(): List<File> {
        val root = File(moduleDir(), "src/main/kotlin/dev/blobs/app")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun moduleDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/res/values").isDirectory) return dir
            dir = dir.parentFile
        }
        error("cannot locate the app module from ${System.getProperty("user.dir")}")
    }

    private object Strings {
        private val resources: Map<String, String> by lazy {
            var root: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (root != null && !File(root, "src/main/res/values").isDirectory) {
                root = root.parentFile
            }
            val values = File(root ?: error("cannot locate res/values"), "src/main/res/values")
            values.listFiles().orEmpty()
                .filter { it.isFile && it.extension == "xml" }
                .flatMap { read(it.readText().replace(COMMENT, "")) }
                .toMap()
        }

        fun read(): Map<String, String> = resources

        fun refs(source: String): Set<String> =
            REF.findAll(source).map { it.groupValues[1] }.toSet()

        fun glyphLabels(source: String): List<String> =
            GLYPH.findAll(source).map { it.groupValues[1] }.toList()

        private fun read(xml: String): List<Pair<String, String>> {
            val single = STRING.findAll(xml).map { it.groupValues[1] to it.groupValues[2] }
            val plural = PLURALS.findAll(xml).map { match ->
                val items = ITEM.findAll(match.groupValues[2]).map { it.groupValues[1] }.toList()
                match.groupValues[1] to (items.maxByOrNull(::words) ?: "")
            }
            return (single + plural).toList()
        }
    }

    private companion object {
        const val MAX_STRING_WORDS = 25
        const val MAX_HEADING_WORDS = 4
        const val MAX_LABEL_WORDS = 3
        const val MAX_WORDS_PER_CONTROL = 8.0
        val COMMENT = Regex("<!--[\\s\\S]*?-->")
        val STRING = Regex("<string name=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</string>")
        val PLURALS = Regex("<plurals name=\"([^\"]+)\">([\\s\\S]*?)</plurals>")
        val ITEM = Regex("<item quantity=\"[^\"]+\">([\\s\\S]*?)</item>")
        val REF = Regex("R\\.(?:string|plurals)\\.([a-z0-9_]+)")
        val GLYPH = Regex(
            "GlyphButton\\(\\s*R\\.drawable\\.[a-z0-9_]+,\\s*" +
                "R\\.string\\.([a-z0-9_]+)"
        )
        val CONTROL = Regex(
            "\\b(DetailDisclosure|TraceCollapsible|Button|TextButton|OutlinedButton|" +
                "FilledIconButton|IconButton|GlyphButton|PrimaryAction|Switch|Checkbox|" +
                "TextField|onClick =)"
        )
        val SET_OF = Regex("setOf\\([\\s\\S]*?\\)")

        /** An equality against a resource is classification, not copy the reader sees. */
        val COMPARISON = Regex("[!=]= R\\.(?:string|plurals)\\.[a-z0-9_]+")

        fun words(value: String): Int = value
            .replace("\\n", " ")
            .replace("&amp;", "&")
            .split(Regex("[\\s·]+"))
            .count { token -> token.any { it.isLetterOrDigit() } }

        fun callArguments(source: String, name: String): List<String> {
            val calls = mutableListOf<String>()
            var index = source.indexOf("$name(")
            while (index >= 0) {
                var depth = 0
                var cursor = index + name.length
                while (cursor < source.length) {
                    when (source[cursor]) {
                        '(' -> depth++

                        ')' -> {
                            depth--
                            if (depth == 0) break
                        }
                    }
                    cursor++
                }
                calls += source.substring(index, cursor.coerceAtMost(source.length))
                index = source.indexOf("$name(", cursor)
            }
            return calls
        }
    }
}
