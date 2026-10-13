package com.simplemobiletools.gallery.pro.databases

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.simplemobiletools.commons.extensions.isGif
import com.simplemobiletools.commons.extensions.isImageFast
import com.simplemobiletools.commons.extensions.isRawFast
import com.simplemobiletools.commons.extensions.isSvg
import com.simplemobiletools.gallery.pro.helpers.TYPE_GIFS
import com.simplemobiletools.gallery.pro.helpers.TYPE_IMAGES
import com.simplemobiletools.gallery.pro.helpers.TYPE_RAWS
import com.simplemobiletools.gallery.pro.helpers.TYPE_SVGS
import java.io.File

/**
 * Access layer for sqlite db files acting as gallery folders.
 * Encrypted dbs are opened with SQLCipher, plain ones with the framework sqlite.
 *
 * Db schema (created by the folder -> db converter):
 *   CREATE TABLE entries (
 *       path TEXT PRIMARY KEY,       -- internal path, no leading slash, "" reserved for the root
 *       parent TEXT NOT NULL,        -- parent internal path, "" for top level
 *       name TEXT NOT NULL,
 *       is_dir INTEGER NOT NULL DEFAULT 0,
 *       data BLOB,                   -- file bytes, null for folders
 *       size INTEGER NOT NULL DEFAULT 0,
 *       last_modified INTEGER NOT NULL DEFAULT 0,
 *       date_taken INTEGER NOT NULL DEFAULT 0
 *   )
 */
object GalleryDbStore {

    private const val TAG = "GalleryDb"

    data class DbEntry(
        val internalPath: String,
        val parent: String,
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val lastModified: Long,
        val dateTaken: Long
    ) {
        val type: Int = typeForName(name)
    }

    data class FolderAgg(
        val internalPath: String,
        val name: String,
        val mediaCnt: Int,
        val size: Long,
        val lastModified: Long,
        val dateTaken: Long,
        val types: Int,
        val coverInternalPath: String
    )

    private val handles = HashMap<String, DbHandle>()
    private val handlesLock = Any()

    private const val FILE_COLUMNS = "path, parent, name, is_dir, size, last_modified, date_taken"

    fun typeForName(name: String): Int = when {
        name.isGif() -> TYPE_GIFS
        name.isRawFast() -> TYPE_RAWS
        name.isSvg() -> TYPE_SVGS
        name.isImageFast() -> TYPE_IMAGES
        else -> 0
    }

    // region connection handling

    private class DbHandle(val plain: SQLiteDatabase?, val cipher: net.sqlcipher.database.SQLiteDatabase?)

    /** minimal common cursor wrapper hiding the differences between the framework and sqlcipher cursors */
    private class RowCursor(private val plainCursor: android.database.Cursor?, private val cipherCursor: net.sqlcipher.Cursor?) {
        fun moveToNext(): Boolean = if (plainCursor != null) plainCursor.moveToNext() else cipherCursor!!.moveToNext()
        fun getString(index: Int): String? = if (plainCursor != null) plainCursor.getString(index) else cipherCursor!!.getString(index)
        fun getLong(index: Int): Long = if (plainCursor != null) plainCursor.getLong(index) else cipherCursor!!.getLong(index)
        fun getBlob(index: Int): ByteArray? = if (plainCursor != null) plainCursor.getBlob(index) else cipherCursor!!.getBlob(index)
        fun moveToFirst(): Boolean = if (plainCursor != null) plainCursor.moveToFirst() else cipherCursor!!.moveToFirst()
        fun close() {
            plainCursor?.close()
            cipherCursor?.close()
        }
    }

    private fun open(context: Context, dbPath: String, password: String): DbHandle {
        synchronized(handlesLock) {
            handles[dbPath]?.let { return it }

            if (!File(dbPath).exists()) {
                throw IllegalStateException("db file does not exist: $dbPath")
            }

            try {
                net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
                val handle = if (password.isEmpty()) {
                    DbHandle(SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE), null)
                } else {
                    DbHandle(
                        null,
                        net.sqlcipher.database.SQLiteDatabase.openDatabase(
                            dbPath, password.toCharArray(), null, net.sqlcipher.database.SQLiteDatabase.OPEN_READWRITE
                        )
                    )
                }

                handles[dbPath] = handle
                return handle
            } catch (e: Throwable) {
                Log.e(TAG, "open failed: $dbPath", e)
                throw e
            }
        }
    }

    fun close(dbPath: String) {
        synchronized(handlesLock) {
            handles.remove(dbPath)?.let {
                try {
                    it.plain?.close()
                    it.cipher?.close()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    /** returns true if the file is an accessible sqlite db containing the "entries" table */
    fun isValidGalleryDb(context: Context, dbPath: String, password: String): Boolean {
        return try {
            val handle = open(context, dbPath, password)
            var valid = false
            handle.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'entries'") {
                valid = it.moveToFirst()
            }
            valid
        } catch (e: Exception) {
            false
        }
    }

    private fun DbHandle.query(sql: String, args: Array<String> = emptyArray(), block: (RowCursor) -> Unit) {
        val cursor = if (plain != null) {
            RowCursor(plain.rawQuery(sql, args), null)
        } else {
            RowCursor(null, cipher!!.rawQuery(sql, args))
        }

        try {
            block(cursor)
        } finally {
            cursor.close()
        }
    }

    // endregion

    // region reads

    private fun readEntry(cursor: RowCursor): DbEntry {
        return DbEntry(
            internalPath = cursor.getString(0) ?: "",
            parent = cursor.getString(1) ?: "",
            name = cursor.getString(2) ?: "",
            isDir = cursor.getLong(3) != 0L,
            size = cursor.getLong(4),
            lastModified = cursor.getLong(5),
            dateTaken = cursor.getLong(6)
        )
    }

    /** aggregates of every folder that directly contains at least one media file, keyed by internal path (root = "") */
    fun getFoldersWithMedia(context: Context, dbPath: String, password: String): Map<String, FolderAgg> {
        val files = ArrayList<DbEntry>()
        val handle = open(context, dbPath, password)
        handle.query("SELECT $FILE_COLUMNS FROM entries WHERE is_dir = 0") { cursor ->
            while (cursor.moveToNext()) {
                files.add(readEntry(cursor))
            }
        }

        val result = HashMap<String, FolderAgg>()
        files.groupBy { it.parent }.forEach { (parent, children) ->
            val media = children.filter { it.type != 0 }
            if (media.isEmpty()) {
                return@forEach
            }

            var types = 0
            media.forEach { types = types or it.type }
            val cover = media.minByOrNull { it.name.toLowerCase() }
            result[parent] = FolderAgg(
                internalPath = parent,
                name = folderName(dbPath, parent),
                mediaCnt = media.size,
                size = media.sumOf { it.size },
                lastModified = media.maxOf { it.lastModified },
                dateTaken = media.maxOf { it.dateTaken },
                types = types,
                coverInternalPath = cover?.internalPath ?: ""
            )
        }

        return result
    }

    private fun folderName(dbPath: String, internalPath: String): String {
        return if (internalPath.isEmpty()) {
            File(dbPath).nameWithoutExtension
        } else {
            internalPath.substringAfterLast('/')
        }
    }

    /** direct media file children of the given internal folder (root = "") */
    fun getFolderMedia(context: Context, dbPath: String, password: String, internalDir: String): ArrayList<DbEntry> {
        val media = ArrayList<DbEntry>()
        val handle = open(context, dbPath, password)
        handle.query("SELECT $FILE_COLUMNS FROM entries WHERE parent = ? AND is_dir = 0", arrayOf(internalDir)) { cursor ->
            while (cursor.moveToNext()) {
                val entry = readEntry(cursor)
                if (entry.type != 0) {
                    media.add(entry)
                }
            }
        }
        return media
    }

    fun readBytes(context: Context, dbPath: String, password: String, internalPath: String): ByteArray? {
        var bytes: ByteArray? = null
        val handle = open(context, dbPath, password)
        handle.query("SELECT length(data) FROM entries WHERE path = ? AND is_dir = 0", arrayOf(internalPath)) { cursor ->
            if (cursor.moveToFirst()) {
                val total = cursor.getLong(0)
                bytes = if (total <= 0L) ByteArray(0) else readBlobInChunks(handle, internalPath, total)
            }
        }
        return bytes
    }

    /** large blobs exceed the cursor window size, they have to be fetched in substr() chunks */
    private fun readBlobInChunks(handle: DbHandle, internalPath: String, total: Long): ByteArray {
        val chunkSize = 512 * 1024
        val result = ByteArray(total.toInt())
        var filled = 0
        var position = 1L   // substr() positions are 1-based
        while (filled < total) {
            val len = minOf(chunkSize.toLong(), total - (position - 1)).toInt()
            val before = filled
            handle.query(
                "SELECT substr(data, ?, ?) FROM entries WHERE path = ? AND is_dir = 0",
                arrayOf(position.toString(), len.toString(), internalPath)
            ) { cursor ->
                if (cursor.moveToFirst()) {
                    val chunk = cursor.getBlob(0)
                    if (chunk != null) {
                        System.arraycopy(chunk, 0, result, filled, chunk.size)
                        filled += chunk.size
                    }
                }
            }
            if (filled == before) {
                throw IllegalStateException("failed to read blob chunk at $position for $internalPath")
            }
            position += len
        }
        return result
    }

    fun readBitmap(context: Context, dbPath: String, password: String, internalPath: String): Bitmap? {
        val bytes = readBytes(context, dbPath, password, internalPath) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // endregion

    // region writes

    fun ensureSchema(context: Context, dbPath: String, password: String) {
        val file = File(dbPath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            if (password.isEmpty()) {
                SQLiteDatabase.openOrCreateDatabase(dbPath, null).close()
            } else {
                net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
                net.sqlcipher.database.SQLiteDatabase.openOrCreateDatabase(dbPath, password.toCharArray(), null).close()
            }
        }

        val handle = open(context, dbPath, password)
        handle.execSQL(
            "CREATE TABLE IF NOT EXISTS entries (" +
                "path TEXT PRIMARY KEY, " +
                "parent TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "is_dir INTEGER NOT NULL DEFAULT 0, " +
                "data BLOB, " +
                "size INTEGER NOT NULL DEFAULT 0, " +
                "last_modified INTEGER NOT NULL DEFAULT 0, " +
                "date_taken INTEGER NOT NULL DEFAULT 0)"
        )
        handle.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_parent ON entries(parent)")
    }

    fun insertFolder(context: Context, dbPath: String, password: String, internalPath: String, name: String) {
        val handle = open(context, dbPath, password)
        handle.execSQL(
            "INSERT OR REPLACE INTO entries (path, parent, name, is_dir, size, last_modified, date_taken) VALUES (?, ?, ?, 1, 0, 0, 0)",
            arrayOf(internalPath, internalPath.substringBeforeLast('/', ""), name)
        )
    }

    fun insertFile(
        context: Context, dbPath: String, password: String, internalPath: String, name: String,
        data: ByteArray, lastModified: Long, dateTaken: Long
    ) {
        val handle = open(context, dbPath, password)
        handle.execSQL(
            "INSERT OR REPLACE INTO entries (path, parent, name, is_dir, data, size, last_modified, date_taken) VALUES (?, ?, ?, 0, ?, ?, ?, ?)",
            arrayOf(internalPath, internalPath.substringBeforeLast('/', ""), name, data, data.size.toLong(), lastModified, dateTaken)
        )
    }

    /** removes the given entries and everything below them */
    fun deleteEntries(context: Context, dbPath: String, password: String, internalPaths: Collection<String>) {
        val handle = open(context, dbPath, password)
        handle.withTransaction {
            internalPaths.forEach { path ->
                handle.execSQL("DELETE FROM entries WHERE path = ?", arrayOf(path))
                handle.execSQL("DELETE FROM entries WHERE parent = ?", arrayOf(path))
                handle.execSQL("DELETE FROM entries WHERE path LIKE ?", arrayOf("$path/%"))
            }
        }
    }

    /** renames a single entry (file or folder), updating all descendant paths too. Returns the new internal path. */
    fun renameEntry(context: Context, dbPath: String, password: String, oldInternalPath: String, newName: String): String {
        val parent = oldInternalPath.substringBeforeLast('/', "")
        val newInternalPath = if (parent.isEmpty()) newName else "$parent/$newName"
        val handle = open(context, dbPath, password)
        handle.withTransaction {
            handle.execSQL("UPDATE entries SET name = ? WHERE path = ?", arrayOf(newName, oldInternalPath))
            handle.execSQL("UPDATE entries SET path = ? WHERE path = ?", arrayOf(newInternalPath, oldInternalPath))
            handle.execSQL("UPDATE entries SET parent = ? WHERE parent = ?", arrayOf(newInternalPath, oldInternalPath))

            val rows = ArrayList<Pair<String, String>>()
            handle.query("SELECT path FROM entries WHERE path LIKE ?", arrayOf("$oldInternalPath/%")) { cursor ->
                while (cursor.moveToNext()) {
                    val oldPath = cursor.getString(0)
                    if (oldPath != null) {
                        rows.add(oldPath to oldPath.replaceFirst(oldInternalPath, newInternalPath))
                    }
                }
            }

            rows.forEach { (oldPath, newPath) ->
                handle.execSQL("UPDATE entries SET path = ? WHERE path = ?", arrayOf(newPath, oldPath))
            }
        }
        return newInternalPath
    }

    private fun DbHandle.withTransaction(block: () -> Unit) {
        if (plain != null) {
            plain.beginTransaction()
            try {
                block()
                plain.setTransactionSuccessful()
            } finally {
                plain.endTransaction()
            }
        } else {
            cipher!!.beginTransaction()
            try {
                block()
                cipher.setTransactionSuccessful()
            } finally {
                cipher.endTransaction()
            }
        }
    }

    /** runs the given block inside a single transaction, all inserts/updates within it are applied together */
    fun <T> runInTransaction(context: Context, dbPath: String, password: String, block: () -> T): T {
        val handle = open(context, dbPath, password)
        var result: T? = null
        handle.withTransaction {
            result = block()
        }
        return result as T
    }

    private fun DbHandle.execSQL(sql: String, args: Array<Any?> = emptyArray()) {
        if (plain != null) {
            plain.execSQL(sql, args)
        } else {
            cipher!!.execSQL(sql, args)
        }
    }

    // endregion
}
