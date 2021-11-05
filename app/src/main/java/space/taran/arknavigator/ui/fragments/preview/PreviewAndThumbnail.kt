package space.taran.arknavigator.ui.fragments.preview

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import space.taran.arknavigator.mvp.model.dao.ResourceId
import space.taran.arknavigator.mvp.model.repo.ResourceMeta
import space.taran.arknavigator.mvp.model.repo.extra.ImageMetaExtra
import space.taran.arknavigator.ui.App
import space.taran.arknavigator.utils.extension
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class PreviewAndThumbnail(val preview: Path, val thumbnail: Path) {

    //todo: separate PreviewRepos similar to ResourceIndexes
    // this would replicate generated previews to other devices
    companion object {

        private val PREVIEWS_STORAGE: Path =
            Paths.get("${App.instance.cacheDir}/previews")
        private val THUMBNAILS_STORAGE: Path =
            Paths.get("${App.instance.cacheDir}/thumbnails")

        private const val THUMBNAIL_WIDTH = 72
        private const val THUMBNAIL_HEIGHT = 128
        private const val COMPRESSION_QUALITY = 100

        private fun previewPath(id: ResourceId): Path =
            PREVIEWS_STORAGE.resolve(id.toString())
        private fun thumbnailPath(id: ResourceId): Path =
            THUMBNAILS_STORAGE.resolve(id.toString())

        init {
            Files.createDirectory(PREVIEWS_STORAGE)
            Files.createDirectory(THUMBNAILS_STORAGE)
        }

        //todo: use index
        fun locate(path: Path, meta: ResourceMeta): PreviewAndThumbnail? {
            val thumbnail = thumbnailPath(meta.id)
            if (!Files.exists(thumbnail)) {
                //means that we couldn't generate anything for this kind of resource
                return null
            }

            if (ImageMetaExtra.ACCEPTED_EXTENSIONS.contains(extension(path))) {
                return PreviewAndThumbnail(
                    preview = path, //using the resource itself as its preview
                    thumbnail = thumbnail)
            }

            return PreviewAndThumbnail(
                preview = previewPath(meta.id),
                thumbnail = thumbnail)
        }

        fun forget(id: ResourceId) {
            Files.delete(previewPath(id))
            Files.delete(thumbnailPath(id))
        }

        fun generate(path: Path, meta: ResourceMeta) {
            if (Files.isDirectory(path)) {
                throw AssertionError("Previews for folders are constant")
            }

            val previewPath = previewPath(meta.id)
            val thumbnailPath = thumbnailPath(meta.id)

            if (Files.exists(previewPath)) {
                if (!Files.exists(thumbnailPath)) {
                    throw AssertionError("Thumbnails must always exist if corresponding preview exists")
                }
                return
            }

            val ext = extension(path)

            if (ImageMetaExtra.ACCEPTED_EXTENSIONS.contains(ext)) {
                // images are special kind of a resource:
                // we don't need to store preview file for them,
                // we only need to downscale them into thumbnail
                val target = ThumbnailTarget()
                Glide.with(App.instance)
                    .asBitmap()
                    .fitCenter()
                    .load(path)
                    .into(target)

                storeThumbnail(thumbnailPath, target.result)

                return
            }

            val generator = PreviewGenerators.BY_EXT[ext]
            if (generator != null) {
                val preview = generator(path)

                val target = ThumbnailTarget()
                Glide.with(App.instance)
                    .asBitmap()
                    .fitCenter()
                    .load(preview)
                    .into(target)

                storePreview(previewPath, preview)
                storeThumbnail(thumbnailPath, target.result)

                return
            }
        }

        private fun storePreview(path: Path, bitmap: Bitmap) =
            storeImage(path, bitmap)

        private fun storeThumbnail(path: Path, bitmap: Bitmap) {
            if (bitmap.width > THUMBNAIL_WIDTH) {
                throw AssertionError("Bitmap must be downscaled")
            }
            if (bitmap.height > THUMBNAIL_HEIGHT) {
                throw AssertionError("Bitmap must be downscaled")
            }

            storeImage(path, bitmap)
        }

        private fun storeImage(target: Path, bitmap: Bitmap) {
            Files.newOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, COMPRESSION_QUALITY, out)
                out.flush()
            }
        }
    }

    private class ThumbnailTarget: CustomTarget<Bitmap>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT) {
        lateinit var result: Bitmap
            private set

        override fun onResourceReady(
            bitmap: Bitmap,
            transition: Transition<in Bitmap>?) {
            result = bitmap
        }

        override fun onLoadCleared(placeholder: Drawable?) {
        }
    }
}