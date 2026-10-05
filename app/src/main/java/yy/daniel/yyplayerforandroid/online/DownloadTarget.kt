package yy.daniel.yyplayerforandroid.online

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.util.UUID

/** Owns only the rows/documents created for one download, including failure cleanup. */
internal class DownloadTarget(private val context: Context, private val tree: Uri?) {
    private val resolver = context.contentResolver
    private val parent = tree?.let { DocumentsContract.buildDocumentUriUsingTree(it, DocumentsContract.getTreeDocumentId(it)) }
    private var pending: Uri? = null
    private var committed = false
    private val siblings = mutableListOf<Uri>()
    val appOwned get() = tree == null
    var name: String = ""
        private set

    fun create(): Uri {
        if (tree != null) require(resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }) {
            "下载目录缺少读写授权，请在设置中重新选择"
        }
        val temporary = "yyplayer-${UUID.randomUUID()}"
        return (if (parent != null) DocumentsContract.createDocument(resolver, parent, "application/octet-stream", "$temporary.partial")
        else {
            if (Build.VERSION.SDK_INT < 29) error("此 Android 版本需先授权 Music 文件夹")
            resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$temporary.mp3")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            })
        } ?: error("无法创建下载文件")).also { pending = it }
    }

    fun finish(base: String, format: AudioFileFormat, song: OnlineSong): Uri {
        val uri = pending ?: error("下载文件未创建")
        val names = existingNames()
        var suffix = 1
        var candidate = "$base.${format.extension}"
        while (candidate in names) { suffix++; candidate = "$base ($suffix).${format.extension}" }
        val result = if (parent != null) DocumentsContract.renameDocument(resolver, uri, candidate)
            ?: error("目录不支持完成下载，请选择其他目录")
        else {
            check(Build.VERSION.SDK_INT >= 29)
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, candidate)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
                put(MediaStore.Audio.Media.TITLE, song.title)
                put(MediaStore.Audio.Media.ARTIST, song.artist)
                put(MediaStore.Audio.Media.ALBUM, song.album)
                // Publish only after the complete file is validated and renamed.
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null) == 1) { "无法完成 Music 下载" }
            uri
        }
        pending = result
        name = resolver.query(result, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else candidate
        } ?: candidate
        return result
    }

    fun sibling(extension: String, bytes: ByteArray): Boolean {
        // Non-media LRC files cannot be inserted in Music via MediaStore; the app keeps the association.
        if (parent == null) return false
        val display = "${name.substringBeforeLast('.')}.$extension"
        // Never overwrite an unrelated existing lyric or cover.
        if (display in existingNames()) return false
        val uri = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", display) ?: return false
        return try { resolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }; siblings += uri; true }
        catch (e: Exception) { runCatching { DocumentsContract.deleteDocument(resolver, uri) }; throw e }
    }

    fun commit() { committed = true }
    fun cleanup() {
        if (committed) return
        siblings.forEach { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        pending?.let { uri -> runCatching {
            if (parent != null) DocumentsContract.deleteDocument(resolver, uri) else resolver.delete(uri, null, null)
        } }
    }

    private fun existingNames(): Set<String> {
        val query = if (tree != null) DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            else {
                if (Build.VERSION.SDK_INT < 29) error("此 Android 版本需先授权 Music 文件夹")
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        val selection = if (tree == null) "${MediaStore.MediaColumns.RELATIVE_PATH} = ?" else null
        val args = if (tree == null) arrayOf("${Environment.DIRECTORY_MUSIC}/") else null
        return resolver.query(query, arrayOf(OpenableColumns.DISPLAY_NAME), selection, args, null)?.use { cursor ->
            buildSet { var count = 0; while (cursor.moveToNext()) { require(++count <= 10000) { "目录文件过多，请选择子目录" }; add(cursor.getString(0)) } }
        }.orEmpty()
    }
}
