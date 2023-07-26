package dev.arkbuilders.navigator.ui.adapter.previewpager

import android.annotation.SuppressLint
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.ortiz.touchview.OnTouchImageViewListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moxy.presenterScope
import space.taran.arklib.ResourceId
import space.taran.arklib.domain.meta.Metadata
import space.taran.arklib.domain.preview.PreviewLocator
import space.taran.arklib.domain.preview.PreviewStatus
import space.taran.arklib.utils.ImageUtils.loadGlideZoomImage
import space.taran.arklib.utils.ImageUtils.loadSubsamplingImage
import dev.arkbuilders.navigator.databinding.ItemImageBinding
import dev.arkbuilders.navigator.mvp.presenter.GalleryPresenter
import dev.arkbuilders.navigator.utils.extensions.makeVisibleAndSetOnClickListener
import timber.log.Timber

@SuppressLint("ClickableViewAccessibility")
class PreviewImageViewHolder(
    private val binding: ItemImageBinding,
    private val presenter: GalleryPresenter,
    private val gestureDetector: GestureDetectorCompat
) : RecyclerView.ViewHolder(binding.root), PreviewItemView {

    private var joinPreviewJob: Job? = null

    init {
        binding.ivSubsampling.setOnTouchListener { view, motionEvent ->
            return@setOnTouchListener gestureDetector.onTouchEvent(motionEvent)
        }
        binding.ivZoom.setOnTouchListener { view, motionEvent ->
            return@setOnTouchListener gestureDetector.onTouchEvent(motionEvent)
        }
        setZoomImageEventListener()
        setSubsamplingEventListener()
    }

    override var pos = -1

    override suspend fun setSource(
        placeholder: Int,
        id: ResourceId,
        meta: Metadata,
        locator: PreviewLocator
    ) = with(binding) {
        joinPreviewJob?.cancel()

        if (meta is Metadata.Video) {
            icPlay.makeVisibleAndSetOnClickListener {
                presenter.onPlayButtonClick()
            }
        } else {
            icPlay.isVisible = false
        }

        if (!locator.isGenerated()) {
            progress.isVisible = true
            Timber.d("join preview generation for $id")
            joinPreviewJob = presenter.presenterScope.launch {
                locator.join()

                if (!isActive) return@launch

                withContext(Dispatchers.Main) {
                    progress.isVisible = false
                    onPreviewReady(placeholder, id, meta, locator)
                }
            }
            return@with
        }

        onPreviewReady(placeholder, id, meta, locator)
    }

    private fun onPreviewReady(
        placeholder: Int,
        id: ResourceId,
        meta: Metadata,
        locator: PreviewLocator
    ) = with(binding) {
        val status = locator.check()
        if (status != PreviewStatus.FULLSCREEN) {
            ivZoom.isZoomEnabled = false
            ivZoom.setImageResource(placeholder)
            return@with
        }

        val path = locator.fullscreen()
        loadGlideZoomImage(id, path, ivZoom)
        loadSubsamplingImage(path, ivSubsampling)
    }

    override fun reset() = with(binding) {
        progress.isVisible = false
        ivZoom.isVisible = true
        ivZoom.isZoomEnabled = true
    }

    fun onRecycled() = with(binding) {
        ivSubsampling.recycle()
        Glide.with(ivZoom.context).clear(ivZoom)
    }

    private fun setZoomImageEventListener() = with(binding) {
        ivZoom.setOnTouchImageViewListener(object : OnTouchImageViewListener {
            override fun onMove() {
                if (ivZoom.isZoomed) {
                    progress.isVisible = true
                    ivZoom.isZoomEnabled = false
                    ivZoom.resetZoom()
                }
            }
        })
    }

    private fun setSubsamplingEventListener() = with(binding) {
        ivSubsampling.setOnImageEventListener(
            object : SubsamplingScaleImageView.OnImageEventListener {
                override fun onReady() {
                    ivZoom.isVisible = false
                    progress.isVisible = false
                    ivZoom.setImageDrawable(null)
                }

                override fun onImageLoadError(e: Exception?) {}

                override fun onPreviewLoadError(e: Exception?) {}

                override fun onImageLoaded() {}

                override fun onTileLoadError(e: Exception?) {}

                override fun onPreviewReleased() {}
            })
    }
}
