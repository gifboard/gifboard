package com.gifboard

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import java.io.File

/**
 * Adapter for displaying local GIF history from files.
 */
class GifHistoryAdapter(
    private val onItemClick: (File) -> Unit,
    private val onDeleteClick: (File) -> Unit
) : RecyclerView.Adapter<GifHistoryAdapter.ViewHolder>() {

    private val files = mutableListOf<File>()

    fun setFiles(newFiles: List<File>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }

    fun removeFile(file: File) {
        val index = files.indexOf(file)
        if (index >= 0) {
            files.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun clear() {
        files.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.gif_history_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount() = files.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: SimpleDraweeView = itemView.findViewById(R.id.history_image)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.history_delete)

        fun bind(file: File) {
            val uri = Uri.fromFile(file)
            
            // Load local file with resizing to save memory
            val request = ImageRequestBuilder.newBuilderWithSource(uri)
                .setResizeOptions(ResizeOptions(200, 200))
                .build()
                
            imageView.controller = Fresco.newDraweeControllerBuilder()
                .setImageRequest(request)
                .setOldController(imageView.controller)
                .setAutoPlayAnimations(true)
                .build()

            itemView.setOnClickListener {
                onItemClick(file)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(file)
            }
        }
    }
}
