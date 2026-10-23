package dev.blobs.app.data.library

import org.json.JSONArray
import org.json.JSONObject

data class Evidence(
    val id: String,
    val title: String,
    val publisher: String,
    val url: String,
    val license: String,
    val updated: String,
    val saved: String,
    val section: String,
    val text: String,
    val edition: String,
    val passageId: Long? = null
) {
    fun json(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("publisher", publisher)
        put("url", url)
        put("license", license)
        put("updated", updated)
        put("saved", saved)
        put("section", section)
        put("text", text)
        put("edition", edition)
        if (passageId != null) put("passageId", passageId)
    }

    companion object {
        fun decodeOrNull(value: String): List<Evidence>? = try {
            decode(value)
        } catch (_: org.json.JSONException) {
            null
        }

        fun encode(items: List<Evidence>): String =
            JSONArray().apply { items.forEach { put(it.json()) } }.toString()

        fun decode(value: String): List<Evidence> {
            val array = JSONArray(value)
            return List(array.length()) { index ->
                val it = array.getJSONObject(index)
                Evidence(
                    it.getString("id"), it.getString("title"), it.getString("publisher"),
                    it.getString("url"), it.getString("license"), it.getString("updated"),
                    it.getString(
                        "saved"
                    ),
                    it.getString("section"), it.getString("text"), it.getString("edition"),
                    it.optLong("passageId").takeIf { passageId -> passageId > 0L }
                )
            }
        }
    }
}
