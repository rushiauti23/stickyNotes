package dev.rushi.stickynotes.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupFormatException(message: String) : Exception(message)

/**
 * Plain-JSON backup file. Import treats the file as untrusted input: it must declare our
 * format, is size- and count-limited, every field is type-checked and clamped, ids are never
 * trusted (notes are always inserted as new), and nothing in the file can schedule a reminder
 * in the past or resurrect a vanished note.
 */
object BackupCodec {
    const val FORMAT = "sticky-notes-backup"
    const val VERSION = 1
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_NOTES = 10_000
    const val MAX_TITLE = 1_000
    const val MAX_CONTENT = 100_000

    fun encode(notes: List<Note>, now: Long = System.currentTimeMillis()): String {
        val array = JSONArray()
        notes.forEach { n ->
            array.put(
                JSONObject()
                    .put("title", n.title)
                    .put("content", n.content)
                    .put("colorIndex", n.colorIndex)
                    .put("pinned", n.pinned)
                    .put("isChecklist", n.isChecklist)
                    .put("archived", n.archived)
                    .put("createdAt", n.createdAt)
                    .put("updatedAt", n.updatedAt)
                    .putOpt("reminderAt", n.reminderAt)
                    .putOpt("expiresAt", n.expiresAt),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", now)
            .put("notes", array)
            .toString(2)
    }

    /** @throws BackupFormatException if the text isn't a valid backup. */
    fun decode(json: String, now: Long = System.currentTimeMillis()): List<Note> {
        if (json.length > MAX_BYTES) throw BackupFormatException("File is too large")
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw BackupFormatException("Not a JSON file")
        }
        if (root.optString("format") != FORMAT) throw BackupFormatException("Not a Sticky Notes backup")
        val version = root.optInt("version", -1)
        if (version < 1 || version > VERSION) throw BackupFormatException("Backup is from a newer app version")
        val array = root.optJSONArray("notes") ?: throw BackupFormatException("Backup has no notes")
        if (array.length() > MAX_NOTES) throw BackupFormatException("Backup has too many notes")

        val notes = ArrayList<Note>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val expiresAt = o.longOrNull("expiresAt")
            if (expiresAt != null && expiresAt <= now) continue // already vanished

            val title = o.stringOrEmpty("title").take(MAX_TITLE)
            val content = o.stringOrEmpty("content").take(MAX_CONTENT)
            if (title.isBlank() && content.isBlank()) continue

            val createdAt = o.longOrNull("createdAt")?.coerceIn(0, now) ?: now
            val updatedAt = o.longOrNull("updatedAt")?.coerceIn(createdAt, now) ?: createdAt
            notes += Note(
                id = 0,
                title = title,
                content = content,
                colorIndex = o.optInt("colorIndex", 0).coerceIn(0, Note.COLOR_COUNT - 1),
                pinned = o.optBoolean("pinned", false),
                isChecklist = o.optBoolean("isChecklist", false),
                archived = o.optBoolean("archived", false),
                createdAt = createdAt,
                updatedAt = updatedAt,
                reminderAt = o.longOrNull("reminderAt")?.takeIf { it > now },
                expiresAt = expiresAt,
                deletedAt = null,
            )
        }
        return notes
    }

    // optString() turns JSON null into the text "null", so check explicitly.
    private fun JSONObject.stringOrEmpty(key: String): String =
        if (isNull(key)) "" else (opt(key) as? String).orEmpty()

    private fun JSONObject.longOrNull(key: String): Long? =
        if (isNull(key)) null else (opt(key) as? Number)?.toLong()
}
