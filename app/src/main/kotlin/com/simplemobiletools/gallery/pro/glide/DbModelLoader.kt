package com.simplemobiletools.gallery.pro.glide

import android.content.Context
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.simplemobiletools.gallery.pro.extensions.readGalleryDbBytes
import com.simplemobiletools.gallery.pro.helpers.GalleryDbs
import java.io.ByteArrayInputStream
import java.io.InputStream

/** Glide model loader making images stored inside gallery sqlite dbs loadable through their virtual "sgdb://" paths */
class DbModelLoader(private val context: Context) : ModelLoader<String, InputStream> {

    override fun buildLoadData(model: String, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        if (!GalleryDbs.isGalleryDbPath(model)) {
            return null
        }
        return ModelLoader.LoadData(ObjectKey(model), DbDataFetcher(context.applicationContext, model))
    }

    override fun handles(model: String) = GalleryDbs.isGalleryDbPath(model)

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
