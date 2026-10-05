package yy.daniel.yyplayerforandroid.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.activity.result.contract.ActivityResultContract

internal data class GrantedDocument(val uri: Uri, val flags: Int) {
    fun persist(context: Context) {
        val access = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        require(access and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) { "文件选择器未授予读取权限" }
        if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) context.contentResolver.takePersistableUriPermission(uri, access)
    }
}

internal class MusicLyricPicker : ActivityResultContract<Unit, GrantedDocument?>() {
    override fun createIntent(context: Context, input: Unit) = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    override fun parseResult(resultCode: Int, intent: Intent?): GrantedDocument? =
        if (resultCode == Activity.RESULT_OK) intent?.data?.let { GrantedDocument(it, intent.flags) } else null
}

internal class MusicTreePicker : ActivityResultContract<Unit, GrantedDocument?>() {
    override fun createIntent(context: Context, input: Unit) = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    override fun parseResult(resultCode: Int, intent: Intent?): GrantedDocument? =
        if (resultCode == Activity.RESULT_OK) intent?.data?.let { GrantedDocument(it, intent.flags) } else null
}

internal class DownloadTreePicker : ActivityResultContract<Unit, GrantedDocument?>() {
    override fun createIntent(context: Context, input: Unit) = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        .putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, "content://com.android.externalstorage.documents/document/primary%3AMusic".toUri())
    override fun parseResult(resultCode: Int, intent: Intent?): GrantedDocument? =
        if (resultCode == Activity.RESULT_OK) intent?.data?.takeIf { intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 }?.let { GrantedDocument(it, intent.flags) } else null
}

internal class LibraryDocumentPicker : ActivityResultContract<Array<String>, List<GrantedDocument>>() {
    override fun createIntent(context: Context, input: Array<String>) = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        .setType(input.singleOrNull() ?: "*/*").putExtra(Intent.EXTRA_MIME_TYPES, input).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    override fun parseResult(resultCode: Int, intent: Intent?): List<GrantedDocument> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val uris = buildList { intent.data?.let(::add); intent.clipData?.let { clip -> repeat(clip.itemCount) { add(clip.getItemAt(it).uri) } } }.distinct()
        return uris.map { GrantedDocument(it, intent.flags) }
    }
}
