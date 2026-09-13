package com.assassinlauncher.launcher.storage

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Exposes this app's own external files (crash logs, instance data, the
 * hs_err files Minecraft writes) to any file picker or "Open from" screen,
 * the same way Amethyst's own files already show up there. Lets Aditya
 * pull a log without hitting Termux's scoped-storage permission wall on
 * another app's Android/data folder.
 *
 * Document IDs are just the file's absolute path under the root - simplest
 * approach for a plain filesystem subtree like this one.
 */
class AppStorageDocumentsProvider : DocumentsProvider() {

    private val defaultRootProjection = arrayOf(
        Root.COLUMN_ROOT_ID,
        Root.COLUMN_DOCUMENT_ID,
        Root.COLUMN_TITLE,
        Root.COLUMN_FLAGS,
        Root.COLUMN_ICON
    )

    private val defaultDocProjection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS
    )

    private fun rootDir(): File = context!!.getExternalFilesDir(null) ?: context!!.filesDir

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: defaultRootProjection)
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT_ID)
            add(Root.COLUMN_DOCUMENT_ID, rootDir().absolutePath)
            add(Root.COLUMN_TITLE, "Assassin Launcher")
            add(Root.COLUMN_FLAGS, 0)
            add(Root.COLUMN_ICON, context!!.applicationInfo.icon)
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: defaultDocProjection)
        addFileRow(cursor, File(documentId))
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(projection ?: defaultDocProjection)
        File(parentDocumentId).listFiles()?.sortedBy { it.name }?.forEach { addFileRow(cursor, it) }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(File(documentId), accessMode)
    }

    override fun getDocumentType(documentId: String): String = mimeTypeOf(File(documentId))

    private fun addFileRow(cursor: MatrixCursor, file: File) {
        val isDir = file.isDirectory
        val flags = if (isDir) {
            Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            Document.FLAG_SUPPORTS_WRITE
        }
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, file.absolutePath)
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_MIME_TYPE, if (isDir) Document.MIME_TYPE_DIR else mimeTypeOf(file))
            add(Document.COLUMN_SIZE, file.length())
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
        }
    }

    private fun mimeTypeOf(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    companion object {
        private const val ROOT_ID = "assassin-launcher-files"
    }
}
