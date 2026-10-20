package dev.blobs.app

import android.os.SystemClock
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import dev.blobs.app.data.library.Evidence
import dev.blobs.app.data.library.LibraryStore
import dev.blobs.app.data.local.PackageEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRetrievalTest {
    @ManualCheck
    @Test
    fun installedLibrariesRetrieveOneQuestion() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val question = requireNotNull(args.getString("retrievalQuestion"))
        val runId = requireNotNull(args.getString("runId"))
        require(runId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as BlobsApplication).container
        val libraries = container.database.packages().all()
            .filter { it.kind == "library" && it.enabled }
        check(libraries.isNotEmpty())
        val output = File(context.filesDir, "retrieval-installed-$runId.json")
        check(!output.exists())
        val start = SystemClock.elapsedRealtime()
        val evidence = LibraryStore().retrieve(libraries, question, null)
        val elapsed = SystemClock.elapsedRealtime() - start
        output.writeText(
            JSONObject()
                .put("question", question)
                .put("libraries", JSONArray(libraries.map { it.key }))
                .put("elapsed_ms", elapsed)
                .put("evidence", JSONArray(Evidence.encode(evidence)))
                .put("scope", "One actual installed-library retrieval; no model generation")
                .put("complete", true)
                .toString(2),
            Charsets.UTF_8
        )
        assertTrue(evidence.isNotEmpty())
        println("Retrieval-only result: $output ($elapsed ms)")
    }

    @Test
    fun scopedSearchKeepsTheConfiguredSectionWeight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val pack = topicLibrary(folder)
            BundledSQLiteDriver().open(pack.path).use { db ->
                listOf(
                    "INSERT INTO passages(doc_id,body,section,title) VALUES ('aster','Fresh orchid meals','Details','')",
                    "INSERT INTO passages(doc_id,body,section,title) VALUES ('aster','Fresh plant meals','Orchid','')",
                    "INSERT INTO passages_fts(passages_fts) VALUES ('rebuild')"
                ).forEach { sql -> db.prepare(sql).use { it.step() } }
            }
            val evidence = LibraryStore().retrieve(listOf(pack), "orchid", setOf("topics/aster"))
            assertEquals(listOf("Orchid", "Details"), evidence.map { it.section })
            assertTrue(evidence.all { it.id == "topics/aster" })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun responseLengthInstructionsDoNotRetrieveGrammarOrPrisonArticles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val pack = topicLibrary(folder)
            BundledSQLiteDriver().open(pack.path).use { db ->
                for ((id, title, body) in listOf(
                    Triple("solar", "Solar panel", "Solar panels convert sunlight to electricity."),
                    Triple(
                        "scam",
                        "Solar panel scam",
                        "A solar panel scam involved fraudulent solar panel projects."
                    ),
                    Triple(
                        "isa",
                        "Is-a",
                        "An is-a relationship in object-oriented programming connects a subtype to a supertype."
                    ),
                    Triple(
                        "prison",
                        "Life sentences",
                        "Consecutive life sentences are prison punishments; these are not two short sentences."
                    )
                )) {
                    db.prepare(
                        "INSERT INTO documents VALUES (?,?,'Author','local:test','CC0','today','today',?,'Test')"
                    ).use {
                        it.bindText(1, id)
                        it.bindText(2, title)
                        it.bindText(3, body)
                        it.step()
                    }
                    db.prepare(
                        "INSERT INTO passages(doc_id,body,section,title) VALUES (?,?,?,?)"
                    ).use {
                        it.bindText(1, id)
                        it.bindText(2, body)
                        it.bindText(3, title)
                        it.bindText(4, title)
                        it.step()
                    }
                }
                db.prepare(
                    "INSERT INTO document_names VALUES ('solar panel','solar'),('is a','isa'),('life sentences','prison')"
                ).use { it.step() }
                db.prepare("INSERT INTO passages_fts(passages_fts) VALUES ('rebuild')").use {
                    it.step()
                }
            }
            val store = LibraryStore()
            val plain = store.retrieve(listOf(pack), "What is a solar panel?", null)
            val styled = store.retrieve(
                listOf(pack),
                "What is a solar panel? Please answer in two short sentences.",
                null
            )
            assertEquals(setOf("Solar panel"), styled.map { it.title }.toSet())
            assertEquals(plain, styled)
            assertEquals(
                setOf("Is-a"),
                store.retrieve(
                    listOf(pack),
                    "Explain the is-a relationship in object-oriented programming.",
                    null
                ).map { it.title }.toSet()
            )
            assertEquals(
                setOf("Life sentences"),
                store.retrieve(listOf(pack), "What are consecutive life sentences?", null)
                    .map { it.title }.toSet()
            )
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun comparisonsKeepNamedAliasesAcrossDifferentTitleLengthsAndEditions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val packs = (1..2).map { edition ->
                val directory = File(folder, "$edition").apply { mkdirs() }
                val pack = topicLibrary(directory)
                BundledSQLiteDriver().open(pack.path).use { db ->
                    val sources = listOf(
                        Triple(
                            "storage",
                            "Long battery energy storage",
                            "Long battery energy storage supplies useful power from saved electricity."
                        ),
                        Triple(
                            "solar",
                            "Solar panel",
                            "Solar panels convert sunlight into useful electrical power."
                        ),
                        Triple(
                            "wind",
                            "Wind turbine",
                            "Wind turbines convert wind into useful electrical power."
                        )
                    )
                    for ((id, title, body) in sources) {
                        db.prepare(
                            "INSERT INTO documents VALUES (?,?,'Author',?,'CC0','today','today',?,'Test')"
                        ).use {
                            it.bindText(1, id)
                            it.bindText(2, title)
                            it.bindText(3, "local:edition-$edition/$id")
                            it.bindText(4, body)
                            it.step()
                        }
                        repeat(if (id == "storage") 3 else 1) { section ->
                            db.prepare(
                                "INSERT INTO passages(doc_id,body,section,title) VALUES (?,?,?,?)"
                            ).use {
                                it.bindText(1, id)
                                it.bindText(2, body)
                                it.bindText(3, if (section == 0) title else "Useful power $section")
                                it.bindText(4, if (section == 0) title else "")
                                it.step()
                            }
                        }
                    }
                    db.prepare(
                        "INSERT INTO document_names VALUES ('long battery energy storage','storage'),('solar panels','solar'),('wind turbines','wind')"
                    ).use { it.step() }
                    db.prepare("INSERT INTO passages_fts(passages_fts) VALUES ('rebuild')").use {
                        it.step()
                    }
                }
                pack.copy(key = "edition-$edition", packageId = "edition-$edition")
            }
            val question = "Why combine long battery energy storage, solar panels and " +
                "wind turbines for useful power?"
            val store = LibraryStore()
            val answerSources = store.retrieve(packs, question, null).take(4)
            assertEquals(
                setOf("Long battery energy storage", "Solar panel", "Wind turbine"),
                answerSources.map { it.title }.toSet()
            )
            val scoped = store.retrieve(packs, question, setOf("edition-1/solar"))
            assertTrue(scoped.isNotEmpty())
            assertTrue(scoped.all { it.id == "edition-1/solar" })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun reorderedArticleTitleRoutesToItsSpecificSections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val pack = topicLibrary(folder)
            BundledSQLiteDriver().open(pack.path).use { db ->
                val titles = listOf("Rail travel in Aster") + (1..12).map {
                    "Rail travel in Country $it"
                }
                titles.forEachIndexed { index, title ->
                    db.prepare(
                        "INSERT INTO documents VALUES (?,?,'Author','local:test','CC0','today','today','Rail travel connects towns.','Test')"
                    ).use {
                        it.bindText(1, "rail$index")
                        it.bindText(2, title)
                        it.step()
                    }
                    db.prepare(
                        "INSERT INTO passages(doc_id,body,section,title) VALUES (?,'Rail travel connects towns.',?,?)"
                    ).use {
                        it.bindText(1, "rail$index")
                        it.bindText(2, title)
                        it.bindText(3, title)
                        it.step()
                    }
                }
                for ((section, text) in listOf(
                    "Train types" to
                        "Local trains stop at each station; limited express trains need an extra ticket.",
                    "Rail pass" to
                        "A visitor rail pass covers local trains, but limited express tickets cost extra.",
                    "Train reservations" to
                        "A visitor can buy a train ticket or pass using the reservation service. The service explains ticket limitations and main travel options."
                )) {
                    db.prepare(
                        "INSERT INTO passages(doc_id,body,section,title) VALUES ('rail0',?,?,'')"
                    ).use {
                        it.bindText(1, text)
                        it.bindText(2, section)
                        it.step()
                    }
                }
                db.prepare("INSERT INTO passages_fts(passages_fts) VALUES ('rebuild')").use {
                    it.step()
                }
            }
            val question = "How can a visitor travel around Aster by rail? " +
                "Compare the main train types and explain ticket or pass limitations using sources."
            val store = LibraryStore()
            val result = store.retrieve(listOf(pack), question, null).take(4)
            assertTrue(
                result.any { it.title == "Rail travel in Aster" && it.section == "Train types" }
            )
            assertTrue(
                result.any { it.title == "Rail travel in Aster" && it.section == "Rail pass" }
            )
            val scoped = store.retrieve(listOf(pack), question, setOf("topics/aster"))
            assertTrue(scoped.all { it.id == "topics/aster" })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun namedSubjectRetrievalKeepsTopicPassagesInsideTheAnswerBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val pack = topicLibrary(folder)
            val store = LibraryStore()
            val question = "Tell me the best vegan restaurants in Aster."
            val result = store.retrieve(listOf(pack), question, null).take(4)
            assertTrue(result.first().text.contains("vegan"))
            assertTrue(
                result.count {
                    it.title in setOf("Aster", "Aster/Harbor") && it.text.contains("vegan")
                } >= 2
            )
            val comparison = store.retrieve(
                listOf(pack),
                "Compare vegan restaurants in Aster and Elsewhere.",
                null
            ).take(4)
            assertTrue(comparison.any { it.title == "Aster" && it.text.contains("vegan") })
            assertTrue(comparison.any { it.title == "Elsewhere" && it.text.contains("Vegan") })
            val overlapping = store.retrieve(
                listOf(pack, pack.copy(key = "regional", packageId = "regional")),
                question,
                null
            ).take(4)
            assertTrue(overlapping.count { it.text.contains("vegan") && it.title == "Aster" } >= 1)
            assertEquals(
                overlapping.size,
                overlapping.map {
                    Triple(it.id.substringAfter('/'), it.section, it.text)
                }.distinct().size
            )
            assertEquals(
                "Aster",
                store.retrieve(listOf(pack), "What should I know before visiting Aster?", null)
                    .first().section
            )
            val scoped = store.retrieve(listOf(pack), question, setOf("topics/elsewhere"))
            assertTrue(scoped.isNotEmpty())
            assertTrue(scoped.all { it.id == "topics/elsewhere" })
            assertTrue(
                store.retrieve(listOf(pack.copy(enabled = false)), question, null).isEmpty()
            )
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun topicLibrary(folder: File): PackageEntity {
        val file = File(folder, "topics.sqlite")
        BundledSQLiteDriver().open(file.path).use { db ->
            listOf(
                "PRAGMA user_version=2",
                "CREATE TABLE documents(id TEXT PRIMARY KEY,title TEXT,publisher TEXT,url TEXT,license TEXT,updated TEXT,saved TEXT,body TEXT,collection TEXT)",
                "CREATE TABLE passages(id INTEGER PRIMARY KEY,doc_id TEXT,body TEXT,section TEXT,title TEXT)",
                "CREATE TABLE document_names(name TEXT,doc_id TEXT,PRIMARY KEY(name,doc_id)) WITHOUT ROWID",
                "CREATE VIRTUAL TABLE passages_fts USING fts5(body,title,section,content='passages',content_rowid='id')",
                "INSERT INTO passages_fts(passages_fts,rank) VALUES ('rank','bm25(1.0,6.0,2.0)')"
            ).forEach { sql -> db.prepare(sql).use { it.step() } }
            val docs = listOf(
                Triple("aster", "Aster", "Aster is a coastal city with a harbor and museums."),
                Triple("harbor", "Aster/Harbor", "The harbor has old warehouses and a ferry."),
                Triple("elsewhere", "Elsewhere", "Vegan restaurants serve plant-based meals."),
                Triple("other-aster", "Aster (another country)", "Vegan restaurants serve meals.")
            ) + (1..12).map { index ->
                Triple(
                    "district$index",
                    "Aster/District $index",
                    "Aster has restaurants and shops."
                )
            }
            for ((id, title, body) in docs) {
                db.prepare(
                    "INSERT INTO documents VALUES (?,?,'Author','local:test','CC0','today','today',?,'Test')"
                ).use {
                    it.bindText(1, id)
                    it.bindText(2, title)
                    it.bindText(3, body)
                    it.step()
                }
                db.prepare(
                    "INSERT INTO passages(doc_id,body,section,title) VALUES (?,?,?,?)"
                ).use {
                    it.bindText(1, id)
                    it.bindText(2, body)
                    it.bindText(3, title)
                    it.bindText(4, title)
                    it.step()
                }
            }
            for ((id, body) in listOf(
                "aster" to "Green Table is a vegan restaurant serving vegetable dishes.",
                "aster" to "Garden Kitchen serves vegan meals without animal products.",
                "harbor" to "Harbor Beans is a vegan cafe with plant-based food."
            )) {
                db.prepare(
                    "INSERT INTO passages(doc_id,body,section,title) VALUES (?,?,'Eat','')"
                ).use {
                    it.bindText(1, id)
                    it.bindText(2, body)
                    it.step()
                }
            }
            db.prepare(
                "INSERT INTO document_names VALUES ('aster','aster'),('elsewhere','elsewhere')"
            ).use { it.step() }
            db.prepare("INSERT INTO passages_fts(passages_fts) VALUES ('rebuild')").use {
                it.step()
            }
        }
        return PackageEntity(
            "topics", "topics", "1", "library", "Topics", file.path, file.length(),
            "0".repeat(64), "CC0", "local:test", 1
        )
    }

    @Test
    fun overlappingRegionalLibrariesDoNotRepeatTheSameEvidenceOrEscapeScope() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val (full, _) = spanBackedLibrary(folder)
            val regional = full.copy(key = "regional", packageId = "regional")
            val store = LibraryStore()
            val result = store.retrieve(listOf(full, regional), "evaporation", null)
            assertEquals(1, result.size)
            assertEquals("span/unicode", result.single().id)
            assertEquals(
                "regional/unicode",
                store.retrieve(
                    listOf(full, regional),
                    "evaporation",
                    setOf("regional/unicode")
                ).single().id
            )
            assertTrue(
                store.retrieve(
                    listOf(full, regional.copy(enabled = false)),
                    "evaporation",
                    setOf("regional/unicode")
                ).isEmpty()
            )
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun spanBackedLibraryReconstructsUnicodeEvidenceAndRejectsInvalidOffsets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val (pack, expected) = spanBackedLibrary(folder)
            val store = LibraryStore()
            store.validate(File(pack.path))
            assertEquals(expected, store.retrieve(listOf(pack), "evaporation", null).single().text)
            BundledSQLiteDriver().open(pack.path).use { db ->
                db.prepare("UPDATE passage_spans SET end=end+1").use { it.step() }
            }
            assertTrue(runCatching { store.validate(File(pack.path)) }.isFailure)
            BundledSQLiteDriver().open(pack.path).use { db ->
                db.prepare("UPDATE passage_spans SET end=end-1").use { it.step() }
                db.prepare("UPDATE documents SET body=body || char(0)").use { it.step() }
            }
            assertTrue(runCatching { store.validate(File(pack.path)) }.isFailure)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun referenceListsCannotCrowdOutEvidenceButRemainReadableInTheArticle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val pack = namedLibrary(folder)
            BundledSQLiteDriver().open(pack.path).use { db ->
                val sections = listOf(
                    "References",
                    "External links",
                    "See also",
                    "Further reading",
                    "Bibliography",
                    " REFERENCES "
                )
                repeat(120) { index ->
                    db.prepare(
                        "INSERT INTO passages(doc_id,body,section,title) VALUES ('ai','neural bibliographyonlytoken',?,'')"
                    ).use {
                        it.bindText(1, sections[index % sections.size])
                        it.step()
                    }
                }
                for (section in listOf("Mechanism", "Notes")) {
                    db.prepare(
                        "INSERT INTO passages(doc_id,body,section,title) VALUES ('ai',?,?, '')"
                    ).use {
                        it.bindText(
                            1,
                            "A neural explanation preserves the relevant mechanism and its limitations."
                        )
                        it.bindText(2, section)
                        it.step()
                    }
                }
                db.prepare(
                    "UPDATE documents SET body=body || '\n\nReferences\nneural bibliographyonlytoken' WHERE id='ai'"
                ).use { it.step() }
                db.prepare(
                    "INSERT INTO documents VALUES ('reference-article','References','Author','local:test','CC0','today','today','References can discuss neural research.','Test')"
                ).use { it.step() }
                db.prepare(
                    "INSERT INTO passages(doc_id,body,section,title) VALUES ('reference-article','References can discuss neural research.','References','References')"
                ).use { it.step() }
                db.prepare("INSERT INTO passages_fts(passages_fts) VALUES ('rebuild')").use {
                    it.step()
                }
            }
            val store = LibraryStore()
            store.validate(File(pack.path))
            val result = store.retrieve(listOf(pack), "neural", setOf("names/ai"))
            assertEquals(setOf("Mechanism", "Notes"), result.map { it.section }.toSet())
            assertTrue(result.none { "bibliographyonlytoken" in it.text })
            assertTrue(store.retrieve(listOf(pack), "bibliographyonlytoken", null).isEmpty())
            assertTrue(
                store.readDocument(listOf(pack), "names/ai")!!.text
                    .contains("References\nneural bibliographyonlytoken")
            )
            assertTrue(
                store.retrieve(listOf(pack), "neural", null)
                    .any { it.id == "names/reference-article" }
            )
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun sourceAliasesImproveShortQuestionsWithoutEscapingScopeOrGuessingAmbiguity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val pack = namedLibrary(folder)
            val store = LibraryStore()
            store.validate(File(pack.path))
            assertEquals(
                "names/ai",
                store.retrieve(listOf(pack), "What is AI?", null).first().id
            )
            assertEquals(
                "names/co2",
                store.retrieve(listOf(pack), "What is CO₂?", null).first().id
            )
            assertTrue(store.retrieve(listOf(pack), "What is sharedname?", null).isEmpty())
            assertEquals(
                "names/jakarta",
                store.retrieve(listOf(pack), "What should I know before visiting Jakarta?", null)
                    .first().id
            )
            val scoped = store.retrieve(listOf(pack), "What is AI?", setOf("names/phonetic"))
            assertEquals(listOf("names/phonetic"), scoped.map { it.id })
            assertTrue(store.retrieve(listOf(pack), "What is CO₂?", setOf("names/ai")).isEmpty())
            BundledSQLiteDriver().open(pack.path).use { db ->
                db.prepare("INSERT INTO document_names VALUES ('invalid','missing')").use {
                    it.step()
                }
            }
            assertTrue(runCatching { store.validate(File(pack.path)) }.isFailure)
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun namedLibrary(folder: File): PackageEntity {
        val file = File(folder, "names.sqlite")
        BundledSQLiteDriver().open(file.path).use { db ->
            listOf(
                "PRAGMA user_version=2",
                "CREATE TABLE documents(id TEXT PRIMARY KEY,title TEXT,publisher TEXT,url TEXT,license TEXT,updated TEXT,saved TEXT,body TEXT,collection TEXT)",
                "CREATE TABLE passages(id INTEGER PRIMARY KEY,doc_id TEXT,body TEXT,section TEXT,title TEXT)",
                "CREATE TABLE document_names(name TEXT,doc_id TEXT,PRIMARY KEY(name,doc_id)) WITHOUT ROWID",
                "CREATE VIRTUAL TABLE passages_fts USING fts5(body,title,section,content='passages',content_rowid='id')",
                "INSERT INTO passages_fts(passages_fts,rank) VALUES ('rank','bm25(1.0,6.0,2.0)')"
            ).forEach { sql -> db.prepare(sql).use { it.step() } }
            val docs = listOf(
                Triple(
                    "ai",
                    "Artificial intelligence",
                    "Computational systems can learn and reason."
                ),
                Triple(
                    "co2",
                    "Carbon dioxide",
                    "A molecule with one carbon atom and two oxygen atoms."
                ),
                Triple("phonetic", "Phonetic table", "The sound ai is listed in this table."),
                Triple(
                    "jakarta",
                    "Jakarta",
                    "Jakarta is a travel destination with airports, routes, neighborhoods and transit."
                )
            ) + (1..32).map { index ->
                Triple(
                    "noise$index",
                    "Travel discussion $index",
                    "What should I know before visiting a place? This discussion explains travel planning and advice."
                )
            }
            for ((id, title, body) in docs) {
                db.prepare(
                    "INSERT INTO documents VALUES (?,?,'Author','local:test','CC0','today','today',?,'Test')"
                ).use {
                    it.bindText(1, id)
                    it.bindText(2, title)
                    it.bindText(3, body)
                    it.step()
                }
                db.prepare(
                    "INSERT INTO passages(doc_id,body,section,title) VALUES (?,?,?,?)"
                ).use {
                    it.bindText(1, id)
                    it.bindText(2, body)
                    it.bindText(3, title)
                    it.bindText(4, title)
                    it.step()
                }
            }
            db.prepare(
                "INSERT INTO document_names VALUES ('ai','ai'),('co2','co2'),('jakarta','jakarta'),('sharedname','ai'),('sharedname','co2')"
            ).use { it.step() }
            db.prepare("INSERT INTO passages_fts(passages_fts) VALUES ('rebuild')").use {
                it.step()
            }
        }
        return PackageEntity(
            "names", "names", "1", "library", "Names", file.path, file.length(),
            "0".repeat(64), "CC0", "local:test", 1
        )
    }

    private fun spanBackedLibrary(folder: File): Pair<PackageEntity, String> {
        val file = File(folder, "span-backed.sqlite")
        val expected = "Evaporation transfers energy from water to the surrounding air."
        val body = "Lead 😀 context.\n\n$expected"
        val startInUtf16 = body.indexOf(expected)
        val start = body.codePointCount(0, startInUtf16)
        val end = start + expected.codePointCount(0, expected.length)
        BundledSQLiteDriver().open(file.path).use { db ->
            listOf(
                "PRAGMA user_version=3",
                "CREATE TABLE documents(id TEXT PRIMARY KEY,title TEXT,publisher TEXT,url TEXT,license TEXT,updated TEXT,saved TEXT,body TEXT,collection TEXT)",
                "CREATE TABLE passages(id INTEGER PRIMARY KEY,doc_id TEXT,section TEXT,title TEXT)",
                "CREATE TABLE passage_spans(passage_id INTEGER PRIMARY KEY,start INTEGER,end INTEGER,section_path TEXT)",
                "CREATE VIRTUAL TABLE passages_fts USING fts5(body,title,section,content='')",
                "INSERT INTO passages_fts(passages_fts,rank) VALUES ('rank','bm25(1.0,6.0,2.0)')"
            ).forEach { sql -> db.prepare(sql).use { it.step() } }
            db.prepare(
                "INSERT INTO documents VALUES ('unicode','Unicode span','Author','local:test','CC0','today','today',?,'Test')"
            ).use {
                it.bindText(1, body)
                it.step()
            }
            db.prepare(
                "INSERT INTO passages VALUES (1,'unicode','Mechanism','Unicode span')"
            ).use { it.step() }
            db.prepare("INSERT INTO passage_spans VALUES (1,?,?,'[\"Mechanism\"]')").use {
                it.bindLong(1, start.toLong())
                it.bindLong(2, end.toLong())
                it.step()
            }
            db.prepare(
                "INSERT INTO passages_fts(rowid,body,title,section) VALUES (1,?,'Unicode span','Mechanism')"
            ).use {
                it.bindText(1, expected)
                it.step()
            }
        }
        return PackageEntity(
            "span", "span", "1", "library", "Span library", file.path, file.length(),
            "0".repeat(64), "CC0", "local:test", 1
        ) to expected
    }

    @Test
    fun evidenceFindsTheMatchingExcerptAcrossLibrariesAndRespectsScope() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val first = library(folder, "a", 8)
            val second = library(folder, "b", 1)
            val store = LibraryStore()
            store.validate(File(first.path))
            val result = store.retrieve(listOf(first, second), "evapotranspiration", null)
            assertEquals(setOf("a/doc", "b/doc"), result.map { it.id }.toSet())
            assertTrue(result.groupingBy { it.id }.eachCount().values.all { it <= 3 })
            assertTrue(result.all { it.text.contains("evapotranspiration") })
            assertTrue(result.all { it.text.length < 1000 })
            val scoped = store.retrieve(listOf(first, second), "evapotranspiration", setOf("b/doc"))
            assertEquals(listOf("b/doc"), scoped.map { it.id })
            assertTrue(store.readDocument(listOf(first, second), "b/doc")!!.text.length > 1000)
            assertTrue(store.retrieve(listOf(first), "evapotranspiration", emptySet()).isEmpty())
            BundledSQLiteDriver().open(second.path).use { db ->
                db.prepare("UPDATE documents SET title=?").use {
                    it.bindText(1, "x".repeat(301))
                    it.step()
                }
            }
            assertTrue(runCatching { store.validate(File(second.path)) }.isFailure)
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun library(folder: File, key: String, passages: Int): PackageEntity {
        val file = File(folder, "$key.sqlite")
        val body = "Background words. ".repeat(100) +
            "Trees use evapotranspiration to cool surrounding air."
        BundledSQLiteDriver().open(file.path).use { db ->
            listOf(
                "PRAGMA user_version=1",
                "CREATE TABLE documents(id TEXT,title TEXT,publisher TEXT,url TEXT,license TEXT,updated TEXT,saved TEXT,body TEXT,collection TEXT)",
                "CREATE TABLE passages(id INTEGER PRIMARY KEY,doc_id TEXT,body TEXT,section TEXT)",
                "CREATE VIRTUAL TABLE passages_fts USING fts5(body,content='passages',content_rowid='id')"
            ).forEach { sql -> db.prepare(sql).use { it.step() } }
            db.prepare(
                "INSERT INTO documents VALUES ('doc',?,'Author','local:test','Test','today','today',?,'Trees')"
            ).use {
                it.bindText(1, "Trees $key")
                it.bindText(2, body)
                it.step()
            }
            repeat(passages) {
                db.prepare(
                    "INSERT INTO passages(doc_id,body,section) VALUES ('doc',?,'Cooling')"
                ).use {
                    it.bindText(1, body)
                    it.step()
                }
            }
            db.prepare("INSERT INTO passages_fts(passages_fts) VALUES ('rebuild')").use {
                it.step()
            }
        }
        return PackageEntity(
            key, key, "1", "library", "Trees $key", file.path, file.length(),
            "0".repeat(64), "Test", "local:test", 1
        )
    }
}
