package dev.arkbuilders.navigator.presentation.screen.gallery.galleryuplift

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView
import dev.arkbuilders.arklib.data.meta.Kind
import dev.arkbuilders.navigator.databinding.ItemImageBinding
import dev.arkbuilders.navigator.databinding.ItemPreviewPlainTextBinding
import dev.arkbuilders.navigator.presentation.screen.gallery.previewpager.PreviewPlainTextViewHolder

class PreviewsPagerUplift(
    val context: Context,
    val viewModel: GalleryUpliftViewModel,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount() = viewModel.galleryItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        if (viewType == Kind.PLAINTEXT.ordinal) {
            PreviewPlainTextViewHolder(
                ItemPreviewPlainTextBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                getGestureDetector()
            )
        } else {
            PreviewImageViewHolderUplift(
                ItemImageBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                viewModel,
                getGestureDetector()
            )
        }

    override fun getItemViewType(position: Int) =
        viewModel.getKind(position)

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (holder) {
            is PreviewPlainTextViewHolderUplift -> {
                holder.pos = position
                viewModel.bindPlainTextView(holder)
            }

            is PreviewImageViewHolderUplift -> {
                holder.pos = position
                viewModel.bindView(holder)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is PreviewImageViewHolderUplift)
            holder.onRecycled()
    }

    private fun getGestureDetector(): GestureDetectorCompat {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                viewModel.onPreviewsItemClick()
                return true
            }
        }
        return GestureDetectorCompat(context, listener)
    }
}