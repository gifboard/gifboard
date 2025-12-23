package com.gifboard

import android.content.Context
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.backends.okhttp3.OkHttpImagePipelineConfigFactory
import com.facebook.imagepipeline.core.DownsampleMode
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Initializes Fresco for GIF loading.
 */
object GifImageLoader {
    
    @Volatile
    private var initialized = false
    
    fun initialize(context: Context) {
        if (initialized) return
        
        synchronized(this) {
            if (initialized) return
            
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            
            val config = OkHttpImagePipelineConfigFactory
                .newBuilder(context.applicationContext, okHttpClient)
                .setDownsampleMode(DownsampleMode.ALWAYS)
                .build()
            
            Fresco.initialize(context.applicationContext, config)
            initialized = true
        }
    }
}
