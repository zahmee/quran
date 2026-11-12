package com.mushaf.reader.share

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Hands a rendered share image to other apps, or to the gallery. */
object ShareImageExport {
    private const val DIR = "shared"

    /** A shared file has to outlive the share sheet: the receiving app may read it well after. */
    private const val KEEP_MS = 60 * 60 * 1000L

    private fun authority(context: Context) = "${context.packageName}.share"

    /** Writes [bitmap] to the cache and returns a content:// URI the receiving app may read. */
    suspend fun toCache(context: Context, bitmap: Bitmap, name: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val stale = System.currentTimeMillis() - KEEP_MS
        dir.listFiles()?.filter { it.lastModified() < stale }?.forEach { it.delete() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, authority(context), file)
    }

    /** The system share sheet for [uri], with [caption] for the apps that take one (WhatsApp does). */
    fun chooser(uri: Uri, caption: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            // The grant travels with the clip through the chooser to the app picked in it.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }

    /** Saving needs no permission from Android 10 on. Before that it would need storage access,
     *  which the app does not ask for — sharing still works there, and can save through other apps. */
    val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, name: String): Boolean =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Natlu")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false
            runCatching {
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    ?: error("no output stream")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }.onFailure { resolver.delete(uri, null, null) }.isSuccess
        }
}
