package com.simplemobiletools.gallery.pro.helpers

import android.content.Context
import com.simplemobiletools.gallery.pro.databases.GalleryDbStore
import java.io.File

/**
 * converts a real folder (with any nesting) into a sqlite db file placed next to the source folder.
 * the original folder and its content are kept untouched.
 * only folders (the whole structure) and media files are written into the db.
 */
object FolderToDbConverter {

    fun convert(
        context: Context,
        sourcePath: String,
        password: String,
        onProgress: (processed: Int, total: Int) -> Unit,
        callback: (success: Boolean, dbPath: String) -> Unit
    ) {
        try {
            val source = File(sourcePath)
            if (!source.isDirectory) {
                callback(false, "")
                return
            }

            val parent = source.parentFile ?: return callback(false, "")
            val parentPath = parent.absolutePath.trimEnd('/')
            var dbPath = "$parentPath/${source.name}.db"
            var index = 1
            while (File(dbPath).exists()) {
                dbPath = "$parentPath/${source.name} (${index}).db"
                index++
            }

            val folders = ArrayList<Pair<String, String>>()   // internalPath to name
            val files = ArrayList<File>()
            collectRecursively(source, "", folders, files)

            if (files.isEmpty() && folders.isEmpty()) {
                callback(false, "")
                return
            }

            GalleryDbStore.ensureSchema(context, dbPath, password)
            GalleryDbStore.runInTransaction(context, dbPath, password) {
                folders.forEach { (internalPath, name) ->
                    GalleryDbStore.insertFolder(context, dbPath, password, internalPath, name)
                }

                files.forEachIndexed { idx, file ->
                    val internalPath = file.absolutePath.removePrefix(source.absolutePath).trim('/')
                    GalleryDbStore.insertFile(
                        context, dbPath, password, internalPath, file.name,
                        file.readBytes(), file.lastModified(), file.lastModified()
                    )
                    onProgress(idx + 1, files.size)
                }
            }

            callback(true, dbPath)
        } catch (e: Exception) {
            callback(false, "")
        }
    }

    private fun collectRecursively(dir: File, internalParent: String, folders: ArrayList<Pair<String, String>>, files: ArrayList<File>) {
        dir.listFiles()?.forEach { item ->
            if (item.isDirectory) {
                val internalPath = if (internalParent.isEmpty()) item.name else "$internalParent/${item.name}"
                folders.add(internalPath to item.name)
                collectRecursively(item, internalPath, folders, files)
            } else if (item.isFile && GalleryDbStore.typeForName(item.name) != 0) {
                files.add(item)
            }
        }
    }
}
