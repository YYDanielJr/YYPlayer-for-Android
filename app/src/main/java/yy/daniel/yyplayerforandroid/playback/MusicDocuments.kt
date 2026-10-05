package yy.daniel.yyplayerforandroid.playback

import android.content.Context
import android.content.UriPermission
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException

internal fun UriPermission.isPersistedTreeCovering(document: Uri): Boolean = runCatching {
    isReadPermission && DocumentsContract.isTreeUri(uri) && DocumentsContract.isTreeUri(document) &&
        uri.authority == document.authority && DocumentsContract.getTreeDocumentId(uri) == DocumentsContract.getTreeDocumentId(document)
}.getOrDefault(false)

internal data class MusicDocument(val uri: Uri, val name: String, val mime: String, val directory: Boolean)

/** Documents provider identities, never absolute filesystem paths. Only explicitly granted trees. */
internal class MusicDocuments(private val context: Context) {
    private val resolver = context.contentResolver
    private val storage = context.getSharedPreferences("music_documents_v1", 0)
    fun rememberTree(tree: Uri) {
        storage.edit { putStringSet("trees", storage.getStringSet("trees", emptySet()).orEmpty() + tree.toString()) }
    }
    private suspend fun children(parent: Uri): List<MusicDocument> {
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        return resolver.query(query, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor -> buildList {
            while (cursor.moveToNext() && size < 20_000) {
                currentCoroutineContext().ensureActive()
                val mime = cursor.getString(2).orEmpty()
                add(MusicDocument(DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0)), cursor.getString(1).orEmpty(),
                    mime, mime == DocumentsContract.Document.MIME_TYPE_DIR))
            }
        } }.orEmpty()
    }
    private fun root(tree: Uri) = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** Bounded recursive import; caller runs on IO and owns cancellation. */
    suspend fun musicIn(tree: Uri): List<Uri> {
        rememberTree(tree)
        val folders = ArrayDeque<Pair<Uri, Int>>(); folders.add(root(tree) to 0)
        val seen = mutableSetOf<String>(); val result = mutableListOf<Uri>(); var inspected = 0
        while (folders.isNotEmpty() && inspected < 20_000) {
            currentCoroutineContext().ensureActive()
            val (parent, depth) = folders.removeFirst()
            if (!seen.add(parent.toString())) continue
            for (child in children(parent)) {
                currentCoroutineContext().ensureActive(); if (++inspected > 20_000) break
                if (child.directory && depth < 16) folders.add(child.uri to depth + 1)
                else if (!child.directory && (child.mime.startsWith("audio/") || child.name.substringAfterLast('.').lowercase() in AudioExtensions)) {
                    result += child.uri
                    storage.edit { putString("parent:${child.uri}", parent.toString()) }
                }
            }
        }
        return result
    }

    suspend fun sibling(media: LocalMedia, names: List<String>): Uri? {
        val parents = linkedSetOf<Uri>()
        storage.getString("parent:${media.uri}", null)?.let { parents += it.toUri() }
        // A selected tree may be exactly the media folder, even for a previously imported single file.
        storage.getStringSet("trees", emptySet()).orEmpty().forEach { saved ->
            val tree = Uri.parse(saved)
            if (resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }) parents += root(tree)
        }
        val pending = ArrayDeque<Pair<Uri, Int>>(); parents.forEach { pending.add(it to 0) }
        val seen = mutableSetOf<String>(); var inspected = 0
        while (pending.isNotEmpty() && inspected < 20_000) {
            val (parent, depth) = pending.removeFirst()
            if (!seen.add(parent.toString())) continue
            currentCoroutineContext().ensureActive()
            val rows = try { children(parent) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
            inspected += rows.size
            // Require the actual audio document in this directory, not just an unrelated same-named lyric.
            val audioId = runCatching { DocumentsContract.getDocumentId(media.uri) }.getOrNull()
            val matching = rows.any { it.uri == media.uri || audioId != null && it.uri.authority == media.uri.authority &&
                runCatching { DocumentsContract.getDocumentId(it.uri) }.getOrNull() == audioId }
            if (!matching) { if (depth < 16) rows.filter { it.directory }.forEach { pending.add(it.uri to depth + 1) }; continue }
            storage.edit { putString("parent:${media.uri}", parent.toString()) }
            for (name in names) rows.firstOrNull { !it.directory && it.name.equals(name, ignoreCase = true) }?.let { return it.uri }
        }
        return null
    }

    companion object { private val AudioExtensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "alac", "aiff", "ape") }
}
