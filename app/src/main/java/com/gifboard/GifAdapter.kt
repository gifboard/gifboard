package com.gifboard

import android.graphics.drawable.Animatable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.imagepipeline.image.ImageInfo

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
    }

    private val gifs = mutableListOf<GifItem>()
    private var isLoading = false
    private var isEndOfList = false
    private var livePreviews = true
    private var insertLinkOnLongPress = false
    private var brokenGifBehavior = "hide" // "overlay", "hide", or "nothing"

    fun setPreferences(livePreviews: Boolean, insertLink: Boolean, brokenBehavior: String) {
        val liveChanged = this.livePreviews != livePreviews
        val behaviorChanged = this.brokenGifBehavior != brokenBehavior
        this.livePreviews = livePreviews
        this.insertLinkOnLongPress = insertLink
        this.brokenGifBehavior = brokenBehavior
        if (liveChanged || behaviorChanged) {
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
        private val brokenOverlay: View = itemView.findViewById(R.id.broken_overlay)
        private val brokenIcon: ImageView = itemView.findViewById(R.id.broken_icon)

        fun bind(gifItem: GifItem) {
            // Show item (might be hidden from previous bind)
            itemView.visibility = View.VISIBLE
            itemView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            
            draweeView.setGifAspectRatio(gifItem.aspectRatio)
            draweeView.colorFilter = null

            // Reset overlay state based on item's current failure state and behavior setting
            updateVisualState(gifItem)

            // Determine if this item should show/play live
            val showLive = livePreviews

            val uri = if (showLive) android.net.Uri.parse(gifItem.url) else android.net.Uri.parse(gifItem.thumbnailUrl ?: gifItem.url)

            // Create controller listener to detect load failures (only matters when live previews enabled)
            val controllerListener = object : BaseControllerListener<ImageInfo>() {
                override fun onFinalImageSet(
                    id: String?,
                    imageInfo: ImageInfo?,
                    animatable: Animatable?
                ) {
                    // Successfully loaded - clear failure state
                    if (gifItem.isFullLoadFailed) {
                        gifItem.isFullLoadFailed = false
                        updateVisualState(gifItem)
                    }
                }

                override fun onFailure(id: String?, throwable: Throwable?) {
                    // Only track failure if live previews enabled (otherwise we're loading thumbnail which usually works)
                    if (showLive) {
                        gifItem.isFullLoadFailed = true
                        updateVisualState(gifItem)
                    }
                }
            }

            val controllerBuilder = Fresco.newDraweeControllerBuilder()
                .setUri(uri)
                .setAutoPlayAnimations(showLive)
                .setRetainImageOnFailure(true)
                .setOldController(draweeView.controller)
                .setControllerListener(controllerListener)

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

        private fun updateVisualState(gifItem: GifItem) {
            val isBroken = gifItem.isFullLoadFailed && livePreviews
            
            when {
                isBroken && brokenGifBehavior == "hide" -> {
                    // Hide the item entirely
                    itemView.visibility = View.GONE
                    itemView.layoutParams.height = 0
                    brokenOverlay.visibility = View.GONE
                    brokenIcon.visibility = View.GONE
                }
                isBroken -> {
                    // Default: "overlay" - show overlay indicator
                    itemView.visibility = View.VISIBLE
                    itemView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    brokenOverlay.visibility = View.VISIBLE
                    brokenIcon.visibility = View.VISIBLE
                }
                else -> {
                    // Not broken - show item normally without overlay
                    itemView.visibility = View.VISIBLE
                    itemView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    brokenOverlay.visibility = View.GONE
                    brokenIcon.visibility = View.GONE
                }
            }
        }
    }

    class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}

