package com.simplemobiletools.gallery.pro.glide

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.os.ParcelFileDescriptor
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.gallery.pro.databases.GalleryDbStore
import com.simplemobiletools.gallery.pro.extensions.getGalleryDbMediaFile
import com.simplemobiletools.gallery.pro.extensions.readGalleryDbBytes
import com.simplemobiletools.gallery.pro.helpers.GalleryDbs
import com.simplemobiletools.gallery.pro.helpers.TYPE_VIDEOS
import java.io.ByteArrayInputStream
import java.io.InputStream

/** Glide model loader making images stored inside gallery sqlite dbs loadable through their virtual "sgdb://" paths */
class DbModelLoader(private val context: Context) : ModelLoader<String, InputStream> {

    override fun buildLoadData(model: String, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        if (!handles(model)) {
            return null
        }
        return ModelLoader.LoadData(ObjectKey(model), DbDataFetcher(context.applicationContext, model))
    }

    // videos are not decodable from a stream, they go through DbVideoLoader instead
    override fun handles(model: String) =
        GalleryDbs.isGalleryDbPath(model) && GalleryDbStore.typeForName(model.getFilenameFromPath()) != TYPE_VIDEOS

    class Factory(private val context: Context) : ModelLoaderFactory<String, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, InputStream> = DbModelLoader(context)
        override fun teardown() {}
    }
}

class DbDataFetcher(private val appContext: Context, private val virtualPath: String) : DataFetcher<InputStream> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        try {
            val bytes = appContext.readGalleryDbBytes(virtualPath)
            if (bytes == null) {
                callback.onLoadFailed(IllegalStateException("no data found for $virtualPath"))
            } else {
                callback.onDataReady(ByteArrayInputStream(bytes))
            }
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {}

    override fun cancel() {}

    override fun getDataClass() = InputStream::class.java

    override fun getDataSource() = DataSource.LOCAL
}

/** Glide model loader extracting db stored videos into the cache, MediaMetadataRetriever only reads file descriptors */
class DbVideoLoader(private val context: Context) : ModelLoader<String, AssetFileDescriptor> {

    override fun buildLoadData(model: String, width: Int, height: Int, options: Options): ModelLoader.LoadData<AssetFileDescriptor>? {
        if (!handles(model)) {
            return null
        }
        return ModelLoader.LoadData(ObjectKey(model), DbVideoFetcher(context.applicationContext, model))
    }

    override fun handles(model: String) =
        GalleryDbs.isGalleryDbPath(model) && GalleryDbStore.typeForName(model.getFilenameFromPath()) == TYPE_VIDEOS

    class Factory(private val context: Context) : ModelLoaderFactory<String, AssetFileDescriptor> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, AssetFileDescriptor> = DbVideoLoader(context)
        override fun teardown() {}
    }
}

class DbVideoFetcher(private val appContext: Context, private val virtualPath: String) : DataFetcher<AssetFileDescriptor> {

    private var descriptor: ParcelFileDescriptor? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in AssetFileDescriptor>) {
        val mediaFile = appContext.getGalleryDbMediaFile(virtualPath)
        if (mediaFile == null) {
            callback.onLoadFailed(IllegalStateException("failed to extract $virtualPath"))
            return
        }

        try {
            val fileDescriptor = ParcelFileDescriptor.open(mediaFile, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor = fileDescriptor
            callback.onDataReady(AssetFileDescriptor(fileDescriptor, 0, mediaFile.length()))
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        try {
            descriptor?.close()
        } catch (ignored: Exception) {
        }

        descriptor = null
    }

    override fun cancel() {}

    override fun getDataClass() = AssetFileDescriptor::class.java

    override fun getDataSource() = DataSource.LOCAL
}
