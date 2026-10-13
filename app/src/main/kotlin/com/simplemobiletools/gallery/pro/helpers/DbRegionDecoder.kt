package com.simplemobiletools.gallery.pro.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import com.davemorrissey.labs.subscaleview.ImageRegionDecoder
import com.simplemobiletools.gallery.pro.extensions.readGalleryDbBytes

/** region decoder for images stored inside gallery sqlite dbs, used by the zoomable photo view */
class DbRegionDecoder : ImageRegionDecoder {
    private var decoder: BitmapRegionDecoder? = null
    private val decoderLock = Any()

    override fun init(context: Context, uri: Uri): Point {
        val bytes = context.readGalleryDbBytes(uri.toString())
            ?: throw IllegalStateException("no data found for ${uri}")
        decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        return Point(decoder!!.width, decoder!!.height)
    }

    override fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap {
        synchronized(decoderLock) {
            val options = BitmapFactory.Options()
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            val bitmap = decoder!!.decodeRegion(rect, options)
            return bitmap ?: throw RuntimeException("Region decoder returned null bitmap - image format may not be supported")
        }
    }

    override fun isReady() = decoder != null && !decoder!!.isRecycled

    override fun recycle() {
        decoder!!.recycle()
    }
}
