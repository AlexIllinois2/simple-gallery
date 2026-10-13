package com.simplemobiletools.gallery.pro.helpers

import java.security.MessageDigest

/**
 * Virtual path scheme for media stored inside sqlite db files.
 *
 * A db file acts like a regular folder (with unlimited nesting):
 *   "sgdb://<dbKey>/@root"                              -> db root folder
 *   "sgdb://<dbKey>/@root/sub"                          -> folder "sub" inside the db
 *   "sgdb://<dbKey>/@root/sub/photo.jpg"                -> image inside the db
 *
 * "dbKey" is a stable hash of the db file absolute path, resolved against [Config.includedDBs].
 * Internal paths of db entries never contain the "@root" segment, it only lives in virtual paths.
 */
object GalleryDbs {

    fun dbKeyForPath(dbPath: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(dbPath.toLowerCase().toByteArray())
        return digest.joinToString("") { String.format("%02x", it) }
    }

    fun dbPathForKey(key: String, includedDBs: Set<String>): String? {
        return includedDBs.firstOrNull { dbKeyForPath(it) == key }
    }

    fun isGalleryDbPath(path: String?): Boolean {
        return path != null && path.startsWith(GDB_PREFIX) && path.contains("/$GDB_ROOT_SEGMENT")
    }

    fun rootVirtualPath(dbPath: String): String {
        return "$GDB_PREFIX${dbKeyForPath(dbPath)}/$GDB_ROOT_SEGMENT"
    }

    fun childVirtualPath(dbPath: String, internalPath: String): String {
        val clean = internalPath.trim('/')
        return if (clean.isEmpty()) {
            rootVirtualPath(dbPath)
        } else {
            "${rootVirtualPath(dbPath)}/$clean"
        }
    }

    fun dbKeyOf(virtualPath: String): String {
        val withoutScheme = virtualPath.removePrefix(GDB_PREFIX)
        return withoutScheme.substringBefore("/$GDB_ROOT_SEGMENT")
    }

    // "" for the db root, "sub" or "sub/deeper" for nested entries
    fun internalPathOf(virtualPath: String): String {
        val withoutScheme = virtualPath.removePrefix(GDB_PREFIX)
        val afterRoot = withoutScheme.substringAfter("/$GDB_ROOT_SEGMENT", "")
        return afterRoot.trim('/')
    }

    fun isRootPath(virtualPath: String): Boolean = internalPathOf(virtualPath).isEmpty()
}
