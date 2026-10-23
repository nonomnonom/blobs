package dev.blobs.app.data.local

import org.json.JSONArray
import org.json.JSONObject

/** Immutable record of the libraries eligible for one research turn. */
internal data class LibraryScopeSnapshot(
    val allReady: Boolean,
    val requested: List<String>,
    val searched: List<LibraryIdentity>,
    val unavailable: List<UnavailableLibrary>
) {
    fun encode(): String = JSONObject().apply {
        put("format", FORMAT)
        put("allReady", allReady)
        put("requested", JSONArray(requested))
        put(
            "searched",
            JSONArray().apply {
                searched.forEach { library ->
                    put(
                        JSONObject().apply {
                            put("key", library.key)
                            put("id", library.id)
                            put("version", library.version)
                            put("sha256", library.sha256)
                            put("title", library.title)
                            if (library.recordsSha256.isNotBlank()) {
                                put("records_sha256", library.recordsSha256)
                            }
                        }
                    )
                }
            }
        )
        put(
            "unavailable",
            JSONArray().apply {
                unavailable.forEach { library ->
                    put(
                        JSONObject().apply {
                            put("selection", library.selection)
                            put("reason", library.reason)
                        }
                    )
                }
            }
        )
    }.toString()

    companion object {
        private const val FORMAT = 1

        fun capture(
            requestedScope: Set<String>?,
            packages: List<PackageEntity>
        ): LibraryScopeSnapshot {
            val requested = requestedScope.orEmpty().sorted()
            val libraries = packages.filter { it.kind == "library" }
            fun selected(pack: PackageEntity): Boolean = requestedScope == null || requested.any {
                it == pack.key || it.startsWith("${pack.key}/")
            }
            val searched = libraries.filter { it.enabled && selected(it) }.map { pack ->
                LibraryIdentity(
                    pack.key,
                    pack.packageId,
                    pack.version,
                    pack.sha256,
                    pack.title,
                    pack.recordsSha256
                )
            }
            val unavailable = requested.mapNotNull { selection ->
                val library = libraries.firstOrNull { pack ->
                    selection == pack.key || selection.startsWith("${pack.key}/")
                }
                when {
                    library == null -> UnavailableLibrary(selection, "not_installed")
                    !library.enabled -> UnavailableLibrary(selection, "disabled")
                    else -> null
                }
            }
            return LibraryScopeSnapshot(requestedScope == null, requested, searched, unavailable)
        }
    }
}

internal data class LibraryIdentity(
    val key: String,
    val id: String,
    val version: String,
    val sha256: String,
    val title: String,
    val recordsSha256: String
)

internal data class UnavailableLibrary(val selection: String, val reason: String)
