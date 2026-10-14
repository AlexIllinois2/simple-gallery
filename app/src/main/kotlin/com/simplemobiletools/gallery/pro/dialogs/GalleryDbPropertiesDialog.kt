package com.simplemobiletools.gallery.pro.dialogs

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Point
import android.widget.LinearLayout
import androidx.exifinterface.media.ExifInterface
import com.simplemobiletools.commons.R as CommonsR
import com.simplemobiletools.commons.dialogs.BasePropertiesDialog
import com.simplemobiletools.commons.extensions.formatAsResolution
import com.simplemobiletools.commons.extensions.formatDate
import com.simplemobiletools.commons.extensions.formatSize
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.getDuration
import com.simplemobiletools.commons.extensions.getExifCameraModel
import com.simplemobiletools.commons.extensions.getExifDateTaken
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.extensions.getParentPath
import com.simplemobiletools.commons.extensions.getVideoResolution
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyTextView
import com.simplemobiletools.gallery.pro.databases.GalleryDbStore
import com.simplemobiletools.gallery.pro.extensions.getGalleryDbEntry
import com.simplemobiletools.gallery.pro.extensions.getGalleryDbMediaFile
import com.simplemobiletools.gallery.pro.extensions.getGalleryDbPathOf
import com.simplemobiletools.gallery.pro.extensions.getGalleryDbSubtreeStats
import com.simplemobiletools.gallery.pro.extensions.readGalleryDbBytes
import com.simplemobiletools.gallery.pro.helpers.GalleryDbs
import com.simplemobiletools.gallery.pro.helpers.TYPE_VIDEOS
import java.io.File

/**
 * Shows the properties of media and folders stored inside sqlite db albums. The commons PropertiesDialog resolves File(path),
 * so it cannot be used with the sgdb:// virtual paths.
 */
class GalleryDbPropertiesDialog(activity: Activity, private val paths: List<String>) : BasePropertiesDialog(activity) {

    private val firstPath = paths.first()
    private val dbPath = activity.getGalleryDbPathOf(firstPath)

    init {
        if (paths.size > 1) {
            addProperty(CommonsR.string.items_selected, paths.size.toString())
        } else {
            addProperty(CommonsR.string.name, getNameLabel())
        }

        val pathLabel = getPathLabel(firstPath)
        if (paths.all { getPathLabel(it) == pathLabel }) {
            addProperty(CommonsR.string.path, pathLabel)
        }
        addProperty(CommonsR.string.size, PLACEHOLDER, CommonsR.id.properties_size)

        activity.getAlertDialogBuilder()
            .setPositiveButton(CommonsR.string.ok, null)
            .apply {
                activity.setupDialogStuff(mDialogView.root, this, CommonsR.string.properties)
            }

        ensureBackgroundThread {
            addRemainingProperties()
        }
    }

    // the db root has no entry of its own, its name is the db file name
    private fun getNameLabel(): String {
        return if (GalleryDbs.isRootPath(firstPath)) {
            File(dbPath ?: "").nameWithoutExtension
        } else {
            firstPath.getFilenameFromPath()
        }
    }

    /** the db file takes the place of the root folder, so the entries show their real nesting */
    private fun getPathLabel(path: String): String? {
        val curDbPath = mActivity.getGalleryDbPathOf(path) ?: return null
        return GalleryDbs.displayPath(path, curDbPath).getParentPath()
    }

    private fun addRemainingProperties() {
        if (paths.size > 1) {
            val size = paths.sumOf { path ->
                val curEntry = mActivity.getGalleryDbEntry(path)
                if (curEntry == null || curEntry.isDir) mActivity.getGalleryDbSubtreeStats(path).second else curEntry.size
            }
            updateValue(CommonsR.id.properties_size, size.formatSize())
            return
        }

        val entry = if (dbPath == null) null else mActivity.getGalleryDbEntry(firstPath)
        if (entry == null && !GalleryDbs.isRootPath(firstPath)) {
            updateValue(CommonsR.id.properties_size, "")
            return
        }

        if (entry == null || entry.isDir) {
            val (mediaCnt, size) = mActivity.getGalleryDbSubtreeStats(firstPath)
            updateValue(CommonsR.id.properties_size, size.formatSize())
            mActivity.runOnUiThread {
                addProperty(CommonsR.string.files_count, mediaCnt.toString())
            }
            return
        }

        if (entry.type == TYPE_VIDEOS) {
            addVideoProperties(entry)
            return
        }

        updateValue(CommonsR.id.properties_size, entry.size.formatSize())

        val bytes = mActivity.readGalleryDbBytes(firstPath)
        val resolution = bytes?.getResolution()
        val exif = bytes?.getExifInterface()
        val latLon = FloatArray(2)
        val hasCoordinates = exif != null && exif.getLatLong(latLon)

        mActivity.runOnUiThread {
            addProperty(CommonsR.string.last_modified, entry.lastModified.formatDate(mActivity))
            resolution?.let { addProperty(CommonsR.string.resolution, it.formatAsResolution()) }

            val dateTaken = exif?.getExifDateTaken(mActivity)?.takeIf { it.isNotEmpty() }
                ?: entry.dateTaken.takeIf { it != 0L }?.formatDate(mActivity)
            dateTaken?.let { addProperty(CommonsR.string.date_taken, it) }

            exif?.getExifCameraModel()?.takeIf { it.isNotEmpty() }?.let { addProperty(CommonsR.string.camera, it) }

            if (hasCoordinates) {
                addProperty(CommonsR.string.gps_coordinates, "${latLon[0]}, ${latLon[1]}")
            }
        }
    }

    /** duration and resolution come from the extracted file, the very same one the player uses */
    private fun addVideoProperties(entry: GalleryDbStore.DbEntry) {
        updateValue(CommonsR.id.properties_size, entry.size.formatSize())
        val mediaFile = mActivity.getGalleryDbMediaFile(firstPath)
        val duration = mediaFile?.let { mActivity.getDuration(it.absolutePath) }
        val resolution = mediaFile?.let { mActivity.getVideoResolution(it.absolutePath) }

        mActivity.runOnUiThread {
            addProperty(CommonsR.string.last_modified, entry.lastModified.formatDate(mActivity))
            duration?.takeIf { it != 0 }?.let { addProperty(CommonsR.string.duration, it.getFormattedDuration()) }
            resolution?.let { addProperty(CommonsR.string.resolution, it.formatAsResolution()) }
        }
    }

    private fun ByteArray.getResolution(): Point? {        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeByteArray(this, 0, size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            Point(options.outWidth, options.outHeight)
        } else {
            null
        }
    }

    /** ExifInterface cannot be built from a byte array, the entry has to be materialized first */
    private fun ByteArray.getExifInterface(): ExifInterface? {
        return try {
            val tmpFile = File.createTempFile("properties", firstPath.getFilenameFromPath(), mActivity.cacheDir)
            try {
                tmpFile.writeBytes(this)
                ExifInterface(tmpFile.absolutePath)
            } finally {
                tmpFile.delete()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updateValue(viewId: Int, value: String) {
        mActivity.runOnUiThread {
            mDialogView.propertiesHolder.findViewById<LinearLayout>(viewId).findViewById<MyTextView>(CommonsR.id.property_value).text = value
        }
    }

    companion object {
        private const val PLACEHOLDER = "…"
    }
}
