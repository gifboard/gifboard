package com.gifboard

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.facebook.drawee.backends.pipeline.Fresco
import org.json.JSONObject

/**
 * RecyclerView adapter for displaying GIF search results.
 */
class GifAdapter(
    private val onGifClick: (String) -> Unit,
    private val onGifLongClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GIF = 0
        private const val VIEW_TYPE_LOADING = 1
        private const val VIEW_TYPE_END = 2
        
        fun parseGifs(jsonResponse: String): List<GifItem> {
            val items = mutableListOf<GifItem>()
            try {
                val json = JSONObject(jsonResponse)
                val ischj = json.optJSONObject("ischj")
                if (ischj != null) {
                    val resultsStr = ischj.optString("results")
                    val results = org.json.JSONArray(resultsStr)
                    for (i in 0 until results.length()) {
                        val gif = results.getJSONObject(i)
                        
                        // Check size limit (10MB max)
                        val sizeStr = gif.optString("os")
                        if (isSizeTooLarge(sizeStr)) continue

                        val url = gif.optString("ou")
                        val thumbnailUrl = gif.optString("tu").takeIf { it.isNotEmpty() }
                        val width = gif.optInt("ow", 200)
                        val height = gif.optInt("oh", 200)
                        if (url.isNotEmpty() && width > 0 && height > 0) {
                            items.add(GifItem(url, thumbnailUrl, width, height))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return items
        }

        private fun isSizeTooLarge(sizeStr: String): Boolean {
            if (sizeStr.isEmpty()) return false
            try {
                val upperStr = sizeStr.uppercase(java.util.Locale.US)
                val value = upperStr.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: return false
                return when {
                    upperStr.endsWith("MB") -> value > 10.0f
                    else -> false
                }
            } catch (e: Exception) {
                return false
            }
        }
    }

    private val gifs = mutableListOf<GifItem>()
    private var isLoading = false
    private var isEndOfList = false
    private var livePreviews = true
    private var insertLinkOnLongPress = false

    fun setPreferences(livePreviews: Boolean, insertLink: Boolean) {
        val liveChanged = this.livePreviews != livePreviews
        this.livePreviews = livePreviews
        this.insertLinkOnLongPress = insertLink
        if (liveChanged) {
            notifyDataSetChanged()
        }
    }

    fun clearAndReset() {
        gifs.clear()
        isLoading = false
        isEndOfList = false
        notifyDataSetChanged()
    }

    fun setGifs(items: List<GifItem>) {
        gifs.clear()
        gifs.addAll(items)
        isEndOfList = items.isEmpty()
        notifyDataSetChanged()
    }

    fun addGifs(items: List<GifItem>) {
        if (items.isEmpty()) {
            if (!isEndOfList) {
                isEndOfList = true
                notifyItemInserted(gifs.size)
            }
            return
        }
        val startPosition = gifs.size
        gifs.addAll(items)
        notifyItemRangeInserted(startPosition, items.size)
    }

    fun clearGifs() {
        gifs.clear()
        isLoading = false
        isEndOfList = false
        notifyDataSetChanged()
    }

    fun setLoading(loading: Boolean) {
        if (isLoading != loading) {
            isLoading = loading
            if (loading) {
                notifyItemInserted(gifs.size)
            } else {
                notifyItemRemoved(gifs.size)
            }
        }
    }

    fun setEndOfList(endOfList: Boolean) {
        if (isEndOfList != endOfList) {
            isEndOfList = endOfList
            if (endOfList) {
                notifyItemInserted(gifs.size)
            } else {
                notifyItemRemoved(gifs.size)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < gifs.size) {
            VIEW_TYPE_GIF
        } else if (isLoading) {
            VIEW_TYPE_LOADING
        } else if (isEndOfList) {
            VIEW_TYPE_END
        } else {
            VIEW_TYPE_LOADING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GIF -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.gif_item, parent, false)
                GifViewHolder(view)
            }
            VIEW_TYPE_END -> {
                val view = TextView(parent.context).apply {
                    text = "No more results"
                    gravity = Gravity.CENTER
                    setPadding(0, 64, 0, 64)
                    setTextColor(0xFF888888.toInt())
                    layoutParams = StaggeredGridLayoutManager.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { isFullSpan = true }
                }
                object : RecyclerView.ViewHolder(view) {}
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.gif_loading_item, parent, false)
                val layoutParams = view.layoutParams
                if (layoutParams is StaggeredGridLayoutManager.LayoutParams) {
                    layoutParams.isFullSpan = true
                }
                LoadingViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is GifViewHolder && position < gifs.size) {
            holder.bind(gifs[position])
        }
    }

    override fun getItemCount(): Int {
        var count = gifs.size
        if (isLoading) count++
        if (isEndOfList && !isLoading) count++
        return count
    }

    inner class GifViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val draweeView: AspectRatioDraweeView = itemView.findViewById(R.id.gif_image)

        fun bind(gifItem: GifItem) {
            draweeView.setGifAspectRatio(gifItem.aspectRatio)
            draweeView.colorFilter = null

            // Determine if this item should show/play live
            val showLive = livePreviews

            val uri = if (showLive) android.net.Uri.parse(gifItem.url) else android.net.Uri.parse(gifItem.thumbnailUrl ?: gifItem.url)

            val controllerBuilder = Fresco.newDraweeControllerBuilder()
                .setUri(uri)
                .setAutoPlayAnimations(showLive)
                .setRetainImageOnFailure(true)
                .setOldController(draweeView.controller)

            // If we are showing live, use thumbnail as low-res placeholder
            if (showLive && gifItem.thumbnailUrl != null) {
                controllerBuilder.setLowResImageRequest(
                    com.facebook.imagepipeline.request.ImageRequest.fromUri(gifItem.thumbnailUrl)
                )
            }

            draweeView.controller = controllerBuilder.build()
            itemView.setOnClickListener { onGifClick(gifItem.url) }
            itemView.setOnLongClickListener { 
                if (insertLinkOnLongPress) {
                    onGifLongClick(gifItem.url)
                    true
                } else {
                    false
                }
            }
        }
    }

    class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
