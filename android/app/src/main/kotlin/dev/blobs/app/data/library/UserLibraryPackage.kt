package dev.blobs.app.data.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import dev.blobs.app.data.local.PackageEntity
import dev.blobs.app.data.packages.PackageManifest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

data class UserDocument(
    val id: String,
    val title: String,
    val publisher: String,
    val url: String,
    val license: String,
    val updated: String,
    val saved: String,
    val body: String
)

class UserLibraryPackage(private val context: Context) {
    private val archiveDirectory = File(context.filesDir, "document-imports").apply { mkdirs() }

    fun discardGeneratedArchive(uri: Uri) {
        if (uri.scheme != "file") return
        val file = File(requireNotNull(uri.path)).canonicalFile
        if (file.parentFile == archiveDirectory.canonicalFile &&
            file.name.startsWith("blobs-user-library-") && file.extension == "blobs"
        ) {
            file.delete()
        }
    }

    fun recoverGeneratedArchives(retained: Uri?) {
        val retainedFile = retained?.takeIf {
            it.scheme == "file"
        }?.path?.let { File(it).canonicalFile }
        archiveDirectory.listFiles().orEmpty().filter { it.canonicalFile != retainedFile }.forEach {
            discardGeneratedArchive(Uri.fromFile(it))
        }
    }

    suspend fun prepare(uris: List<Uri>): File {
        require(uris.isNotEmpty() && uris.size <= MAX_DOCUMENTS) { "DOCUMENT_COUNT" }
        val jobContext = currentCoroutineContext()
        val budget = InputBudget()
        val imported = try {
            uris.mapIndexed { index, uri ->
                jobContext.ensureActive()
                read(uri, index, budget, jobContext::ensureActive)
            }
        } catch (pack: CollectionPackSelected) {
            require(uris.size == 1) { "PACK_MIXED_IMPORT" }
            pack.documents.map { ImportedDocument(it, pack.title) }
        }
        require(
            imported.sumOf { it.document.body.codePointCount(0, it.document.body.length) } <=
                MAX_COLLECTION_CODEPOINTS
        ) {
            "COLLECTION_TOO_LARGE"
        }
        val packTitle = imported.firstOrNull()?.collectionTitle ?: collectionTitle(imported)
        val documents = imported.mapIndexed { index, item ->
            item.document.copy(id = "document-${(index + 1).toString().padStart(4, '0')}")
        }
        validateLibraryInput(packTitle, documents)
        val database = File.createTempFile("blobs-user-library-", ".sqlite", context.cacheDir)
        val archive = File.createTempFile("blobs-user-library-", ".blobs", archiveDirectory)
        try {
            jobContext.ensureActive()
            buildDatabase(database, packTitle, documents, jobContext::ensureActive)
            val manifest = PackageManifest(
                id = "user-${UUID.randomUUID().toString().replace("-", "").take(24)}",
                version = "1",
                kind = "library",
                title = packTitle,
                bytes = database.length(),
                sha256 = sha256(database, jobContext::ensureActive),
                license = "User-provided documents",
                origin = "local:user-documents"
            )
            ZipOutputStream(FileOutputStream(archive)).use { output ->
                jobContext.ensureActive()
                output.putNextEntry(ZipEntry("manifest.json"))
                output.write(manifest.json().toByteArray(StandardCharsets.UTF_8))
                output.closeEntry()
                output.putNextEntry(ZipEntry("library.sqlite"))
                copyFile(database, output, jobContext::ensureActive)
                output.closeEntry()
            }
            return archive
        } catch (error: Exception) {
            archive.delete()
            throw error
        } finally {
            database.delete()
        }
    }

    fun export(pack: PackageEntity, library: LibraryStore, output: OutputStream) {
        OutputStreamWriter(output, StandardCharsets.UTF_8).buffered().use { writer ->
            require(pack.origin == "local:user-documents") { "NOT_USER_COLLECTION" }
            var count = 0
            var codePoints = 0L
            writer.append("{\"format\":\"blobs-collection\",\"version\":1,\"title\":")
            writer.append(JSONObject.quote(pack.title))
            writer.append(",\"documents\":[")
            library.forEachDocument(pack) { document ->
                count++
                require(count <= MAX_DOCUMENTS) { "DOCUMENT_COUNT" }
                codePoints += document.body.codePointCount(0, document.body.length)
                require(codePoints <= MAX_COLLECTION_CODEPOINTS) { "COLLECTION_TOO_LARGE" }
                if (count > 1) writer.append(',')
                writer.append("{\"title\":${JSONObject.quote(document.title)}")
                writer.append(",\"publisher\":${JSONObject.quote(document.publisher)}")
                writer.append(",\"url\":${JSONObject.quote(document.url)}")
                writer.append(",\"license\":${JSONObject.quote(document.license)}")
                writer.append(",\"updated\":${JSONObject.quote(document.updated)}")
                writer.append(",\"saved\":${JSONObject.quote(document.saved)}")
                writer.append(",\"body\":${JSONObject.quote(document.body)}}")
            }
            require(count > 0) { "DOCUMENT_COUNT" }
            writer.append("]}")
        }
    }

    private fun read(
        uri: Uri,
        index: Int,
        budget: InputBudget,
        checkActive: () -> Unit
    ): ImportedDocument {
        val name = displayName(uri).ifBlank { "Document ${index + 1}" }
        val extension = name.substringAfterLast('.', "").lowercase()
        require(extension in setOf("txt", "md", "markdown", "csv", "json", "pdf")) {
            "UNSUPPORTED_DOCUMENT"
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                budget.add(count)
                val perFileLimit = if (extension == "json") MAX_IMPORT_BYTES else MAX_FILE_BYTES
                require(output.size() + count <= perFileLimit) { "DOCUMENT_TOO_LARGE" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IOException("Cannot read document")
        val text = when (extension) {
            "pdf" -> readPdf(bytes, checkActive)
            "json" -> readJsonOrDocumentPack(bytes, name)
            else -> strictUtf8(bytes)
        }
        val body = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        require(body.isNotEmpty()) { if (extension == "pdf") "PDF_NO_TEXT" else "EMPTY_DOCUMENT" }
        require(body.codePointCount(0, body.length) <= MAX_DOCUMENT_CODEPOINTS) {
            "DOCUMENT_TOO_LARGE"
        }
        require(
            '\u0000' !in body && '\uFFFD' !in body && !body.hasUnpairedSurrogate()
        ) { "DOCUMENT_INVALID" }
        val title = name.substringBeforeLast('.', name).trim().ifBlank { "Untitled document" }
        val now = java.time.Instant.now().toString()
        return ImportedDocument(
            document = UserDocument(
                id = "",
                title = truncateUtf16(title, 300),
                publisher = "Your files",
                url = "",
                license = "User-provided content",
                updated = "",
                saved = now,
                body = body
            )
        )
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
    }.getOrDefault("").ifBlank { uri.lastPathSegment.orEmpty() }

    private fun readJsonOrDocumentPack(bytes: ByteArray, name: String): String {
        val text = strictUtf8(bytes)
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return text
        if (root.optString("format") != PACK_FORMAT) return text
        require(root.optInt("version") == PACK_VERSION) { "PACK_VERSION" }
        val collectionTitle = root.optString("title").trim()
        require(collectionTitle.length in 1..MAX_TITLE_LENGTH) { "PACK_INVALID" }
        val rows = root.optJSONArray("documents") ?: throw IllegalArgumentException("PACK_INVALID")
        require(rows.length() in 1..MAX_DOCUMENTS) { "DOCUMENT_COUNT" }
        val packDocuments = (0 until rows.length()).map { index ->
            val row = rows.optJSONObject(index) ?: throw IllegalArgumentException("PACK_INVALID")
            val title = row.optString("title").trim()
            val body = row.optString("body").trim()
            require(title.isNotBlank() && title.length <= 300 && body.isNotBlank()) {
                "PACK_INVALID"
            }
            require(body.codePointCount(0, body.length) <= MAX_DOCUMENT_CODEPOINTS) {
                "DOCUMENT_TOO_LARGE"
            }
            require(
                '\u0000' !in body && '\uFFFD' !in body && !body.hasUnpairedSurrogate()
            ) { "PACK_INVALID" }
            UserDocument(
                id = "",
                title = title,
                publisher = row.optString("publisher", collectionTitle),
                url = row.optString("url"),
                license = row.optString("license", "User-provided content"),
                updated = row.optString("updated"),
                saved = row.optString("saved"),
                body = body
            )
        }
        require(
            packDocuments.sumOf { it.body.codePointCount(0, it.body.length) } <=
                MAX_COLLECTION_CODEPOINTS
        ) {
            "COLLECTION_TOO_LARGE"
        }
        throw CollectionPackSelected(collectionTitle, packDocuments)
    }

    private fun readPdf(bytes: ByteArray, checkActive: () -> Unit): String {
        PDFBoxResourceLoader.init(context)
        try {
            PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
                checkActive()
                require(!document.isEncrypted) { "PDF_PASSWORD" }
                require(document.numberOfPages in 1..MAX_PDF_PAGES) { "PDF_PAGE_COUNT" }
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                val writer = LimitedWriter(MAX_DOCUMENT_CODEPOINTS, checkActive)
                stripper.writeText(document, writer)
                return writer.text()
            }
        } catch (error: InvalidPasswordException) {
            throw IllegalArgumentException("PDF_PASSWORD", error)
        } catch (error: NoClassDefFoundError) {
            throw IllegalArgumentException("PDF_UNSUPPORTED_ENCRYPTION", error)
        }
    }

    private fun strictUtf8(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    private fun collectionTitle(documents: List<ImportedDocument>): String {
        if (documents.size == 1) {
            return truncateUtf16(documents.first().document.title, MAX_TITLE_LENGTH)
        }
        return "Imported documents (${documents.size})"
    }

    private fun validateLibraryInput(collection: String, documents: List<UserDocument>) {
        require(validMetadataText(collection, MAX_TITLE_LENGTH, required = true)) {
            "DOCUMENT_INVALID"
        }
        documents.forEach { document ->
            require(
                validMetadataText(document.title, 300, required = true) &&
                    validMetadataText(document.publisher, 300) &&
                    validMetadataText(document.url, 2_000) &&
                    validMetadataText(document.license, 4_000) &&
                    validMetadataText(document.updated, 80) &&
                    validMetadataText(document.saved, 80) &&
                    document.body.isNotBlank() &&
                    '\u0000' !in document.body &&
                    '\uFFFD' !in document.body &&
                    !document.body.hasUnpairedSurrogate()
            ) {
                "DOCUMENT_INVALID"
            }
        }
    }

    private fun validMetadataText(value: String, maximumLength: Int, required: Boolean = false): Boolean =
        value.length <= maximumLength &&
            '\u0000' !in value &&
            '\uFFFD' !in value &&
            !value.hasUnpairedSurrogate() &&
            (!required || value.any { !it.isWhitespace() })

    private fun String.hasUnpairedSurrogate(): Boolean {
        var index = 0
        while (index < length) {
            when {
                Character.isHighSurrogate(this[index]) -> {
                    if (index + 1 == length || !Character.isLowSurrogate(this[index + 1])) {
                        return true
                    }
                    index += 2
                }

                Character.isLowSurrogate(this[index]) -> return true
                else -> index++
            }
        }
        return false
    }

    private fun truncateUtf16(value: String, maximumLength: Int): String {
        var end = minOf(value.length, maximumLength)
        if (
            end < value.length && end > 0 &&
            Character.isHighSurrogate(value[end - 1]) &&
            Character.isLowSurrogate(value[end])
        ) {
            end--
        }
        return value.substring(0, end)
    }

    private fun buildDatabase(
        file: File,
        title: String,
        documents: List<UserDocument>,
        checkActive: () -> Unit
    ) {
        val db = BundledSQLiteDriver().open(file.path)
        try {
            execute(db, "PRAGMA journal_mode=DELETE")
            execute(db, "PRAGMA user_version=3")
            execute(
                db,
                "CREATE TABLE documents(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,publisher TEXT NOT NULL,url TEXT NOT NULL,license TEXT NOT NULL,updated TEXT NOT NULL,saved TEXT NOT NULL,body TEXT NOT NULL,collection TEXT NOT NULL)"
            )
            execute(db, "CREATE INDEX documents_title ON documents(title,id)")
            execute(
                db,
                "CREATE TABLE passages(id INTEGER PRIMARY KEY,doc_id TEXT NOT NULL,section TEXT NOT NULL,title TEXT NOT NULL)"
            )
            execute(
                db,
                "CREATE VIRTUAL TABLE passages_fts USING fts5(body,title,section,content='',tokenize='unicode61')"
            )
            execute(
                db,
                "INSERT INTO passages_fts(passages_fts,rank) VALUES ('rank','bm25(1.0,6.0,2.0)')"
            )
            execute(db, "CREATE INDEX passages_document ON passages(doc_id)")
            execute(
                db,
                "CREATE TABLE passage_spans(passage_id INTEGER PRIMARY KEY,start INTEGER NOT NULL,end INTEGER NOT NULL,section_path TEXT NOT NULL)"
            )
            execute(db, "BEGIN IMMEDIATE")
            insertDocuments(db, title, documents, checkActive)
            execute(db, "INSERT INTO passages_fts(passages_fts,rank) VALUES ('integrity-check',1)")
            execute(db, "COMMIT")
            db.prepare("PRAGMA quick_check").use { check(it.step() && it.getText(0) == "ok") }
        } catch (error: Exception) {
            runCatching { execute(db, "ROLLBACK") }
            throw error
        } finally {
            db.close()
        }
    }

    private fun insertDocuments(
        db: SQLiteConnection,
        collection: String,
        documents: List<UserDocument>,
        checkActive: () -> Unit
    ) {
        val documentInsert = db.prepare(
            "INSERT INTO documents VALUES (?,?,?,?,?,?,?,?,?)"
        )
        val passageInsert = db.prepare(
            "INSERT INTO passages(id,doc_id,section,title) VALUES (?,?,?,?)"
        )
        val ftsInsert = db.prepare(
            """
            INSERT INTO passages_fts(rowid,body,title,section)
            VALUES (?,?,?,?)
            """.trimIndent()
        )
        val spanInsert = db.prepare("INSERT INTO passage_spans VALUES (?,?,?,?)")
        try {
            var passageId = 1L
            documents.forEach { document ->
                checkActive()
                bind(
                    documentInsert,
                    listOf(
                        document.id,
                        document.title,
                        document.publisher,
                        document.url,
                        document.license,
                        document.updated,
                        document.saved,
                        document.body,
                        collection
                    )
                )
                documentSpans(document.body).forEachIndexed { index, span ->
                    checkActive()
                    passageInsert.bindLong(1, passageId)
                    passageInsert.bindText(2, document.id)
                    passageInsert.bindText(3, document.title)
                    passageInsert.bindText(4, if (index == 0) document.title else "")
                    passageInsert.step()
                    passageInsert.reset()
                    passageInsert.clearBindings()
                    ftsInsert.bindLong(1, passageId)
                    ftsInsert.bindText(2, span.text)
                    ftsInsert.bindText(3, if (index == 0) document.title else "")
                    ftsInsert.bindText(4, document.title)
                    ftsInsert.step()
                    ftsInsert.reset()
                    ftsInsert.clearBindings()
                    spanInsert.bindLong(1, passageId)
                    spanInsert.bindLong(2, span.startCodePoint.toLong())
                    spanInsert.bindLong(3, span.endCodePoint.toLong())
                    spanInsert.bindText(4, JSONArray().put(document.title).toString())
                    spanInsert.step()
                    spanInsert.reset()
                    spanInsert.clearBindings()
                    passageId++
                }
            }
        } finally {
            documentInsert.close()
            passageInsert.close()
            ftsInsert.close()
            spanInsert.close()
        }
    }

    private fun bind(statement: SQLiteStatement, values: List<String>) {
        values.forEachIndexed { index, value -> statement.bindText(index + 1, value) }
        statement.step()
        statement.reset()
        statement.clearBindings()
    }

    private fun execute(db: SQLiteConnection, sql: String) {
        db.prepare(sql).use { it.step() }
    }

    private fun copyFile(file: File, output: OutputStream, checkActive: () -> Unit) {
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
        }
    }

    private fun sha256(file: File, checkActive: () -> Unit): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class ImportedDocument(
        val document: UserDocument,
        val collectionTitle: String? = null
    )
    private class InputBudget(var bytes: Long = 0) {
        fun add(count: Int) {
            bytes += count
            require(bytes <= MAX_IMPORT_BYTES) { "IMPORT_TOO_LARGE" }
        }
    }
    private class CollectionPackSelected(val title: String, val documents: List<UserDocument>) :
        IllegalArgumentException("COLLECTION_PACK")

    private class LimitedWriter(
        private val maxCodePoints: Int,
        private val checkActive: () -> Unit
    ) : Writer() {
        private val output = StringBuilder()

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            checkActive()
            require(output.length + len <= maxCodePoints * 2) { "DOCUMENT_TOO_LARGE" }
            output.append(cbuf, off, len)
        }

        override fun flush() = Unit
        override fun close() = Unit
        fun text(): String {
            val value = output.toString()
            require(value.codePointCount(0, value.length) <= maxCodePoints) { "DOCUMENT_TOO_LARGE" }
            return value
        }
    }

    companion object {
        const val PACK_FORMAT = "blobs-collection"
        const val PACK_VERSION = 1
        private const val MAX_DOCUMENTS = 50
        private const val MAX_PDF_PAGES = 200
        private const val MAX_FILE_BYTES = 10 * 1024 * 1024
        private const val MAX_IMPORT_BYTES = 25 * 1024 * 1024
        private const val MAX_DOCUMENT_CODEPOINTS = 1_000_000
        private const val MAX_COLLECTION_CODEPOINTS = 5_000_000
        private const val MAX_TITLE_LENGTH = 160
    }
}
