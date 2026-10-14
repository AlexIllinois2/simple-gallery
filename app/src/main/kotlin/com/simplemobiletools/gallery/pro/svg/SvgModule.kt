package com.simplemobiletools.gallery.pro.svg

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.drawable.PictureDrawable

import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.caverock.androidsvg.SVG
import com.simplemobiletools.gallery.pro.glide.DbModelLoader
import com.simplemobiletools.gallery.pro.glide.DbVideoLoader

import java.io.InputStream

@GlideModule
class SvgModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.register(SVG::class.java, PictureDrawable::class.java, SvgDrawableTranscoder()).append(InputStream::class.java, SVG::class.java, SvgDecoder())
        // images stored in gallery sqlite dbs load through their virtual paths
        registry.prepend(String::class.java, InputStream::class.java, DbModelLoader.Factory(context))
        // so do the videos, but those need a file descriptor for frame extraction
        registry.prepend(String::class.java, AssetFileDescriptor::class.java, DbVideoLoader.Factory(context))
    }

    override fun isManifestParsingEnabled() = false
}
