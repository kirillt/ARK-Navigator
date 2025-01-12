package dev.arkbuilders.navigator.presentation.screen.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.arkbuilders.arkfilepicker.folders.RootAndFav
import dev.arkbuilders.arklib.ResourceId
import dev.arkbuilders.arklib.data.Message
import dev.arkbuilders.arklib.data.index.ResourceIndex
import dev.arkbuilders.arklib.data.index.ResourceIndexRepo
import dev.arkbuilders.arklib.data.meta.Metadata
import dev.arkbuilders.arklib.data.meta.MetadataProcessor
import dev.arkbuilders.arklib.data.meta.MetadataProcessorRepo
import dev.arkbuilders.arklib.data.preview.PreviewProcessor
import dev.arkbuilders.arklib.data.preview.PreviewProcessorRepo
import dev.arkbuilders.arklib.data.stats.StatsEvent
import dev.arkbuilders.arklib.data.storage.StorageException
import dev.arkbuilders.arklib.user.score.ScoreStorage
import dev.arkbuilders.arklib.user.score.ScoreStorageRepo
import dev.arkbuilders.arklib.user.tags.Tag
import dev.arkbuilders.arklib.user.tags.TagStorage
import dev.arkbuilders.arklib.user.tags.TagsStorageRepo
import dev.arkbuilders.components.scorewidget.ScoreWidgetController
import dev.arkbuilders.navigator.analytics.gallery.GalleryAnalytics
import dev.arkbuilders.navigator.data.preferences.PreferenceKey
import dev.arkbuilders.navigator.data.preferences.Preferences
import dev.arkbuilders.navigator.data.stats.StatsStorage
import dev.arkbuilders.navigator.data.stats.StatsStorageRepo
import dev.arkbuilders.navigator.data.utils.LogTags
import dev.arkbuilders.navigator.presentation.navigation.AppRouter
import dev.arkbuilders.navigator.presentation.navigation.Screens
import dev.arkbuilders.navigator.presentation.screen.gallery.domain.GalleryItem
import dev.arkbuilders.navigator.presentation.screen.gallery.domain.GallerySideEffect
import dev.arkbuilders.navigator.presentation.screen.gallery.domain.GalleryState
import dev.arkbuilders.navigator.presentation.screen.gallery.domain.ProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.syntax.simple.blockingIntent
import org.orbitmvi.orbit.syntax.simple.intent
import org.orbitmvi.orbit.syntax.simple.postSideEffect
import org.orbitmvi.orbit.syntax.simple.reduce
import org.orbitmvi.orbit.viewmodel.container
import timber.log.Timber
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.notExists

class GalleryViewModel(
    startPos: Int,
    selectingEnabled: Boolean,
    selectedResources: List<ResourceId>,
    rootAndFav: RootAndFav,
    resourcesIds: List<ResourceId>,
    val preferences: Preferences,
    val router: AppRouter,
    val indexRepo: ResourceIndexRepo,
    val previewStorageRepo: PreviewProcessorRepo,
    val metadataStorageRepo: MetadataProcessorRepo,
    val tagsStorageRepo: TagsStorageRepo,
    val statsStorageRepo: StatsStorageRepo,
    val scoreStorageRepo: ScoreStorageRepo,
    val analytics: GalleryAnalytics
) : ContainerHost<GalleryState, GallerySideEffect>, ViewModel() {
    private lateinit var index: ResourceIndex
    private lateinit var tagsStorage: TagStorage
    private lateinit var previewStorage: PreviewProcessor
    private lateinit var metadataStorage: MetadataProcessor
    private lateinit var statsStorage: StatsStorage
    private lateinit var scoreStorage: ScoreStorage

    override val container: Container<GalleryState, GallerySideEffect> =
        container(
            GalleryState(
                rootAndFav = rootAndFav,
                resourcesIds = resourcesIds,
                currentPos = startPos,
                selectingEnabled = selectingEnabled,
                selectedResources = selectedResources
            )
        )

    val scoreWidgetController = ScoreWidgetController(
        scope = viewModelScope,
        getCurrentId = { container.stateFlow.value.currentItem.id() },
        onScoreChanged = {
            intent {
                postSideEffect(GallerySideEffect.NotifyResourceScoresChanged)
            }
        }
    )

    private val messageFlow: MutableSharedFlow<Message> = MutableSharedFlow()

    init {
        initStorages()
        intent {
            postSideEffect(GallerySideEffect.ScrollToPage(state.currentPos))
        }
    }

    fun onPreviewsItemClick() {
        intent {
            reduce { state.copy(controlsVisible = !state.controlsVisible) }
        }
    }

    fun onRemoveFabClick() = viewModelScope.launch(NonCancellable) {
        intent {
            analytics.trackResRemove()
            Timber.d(
                LogTags.GALLERY_SCREEN,
                buildString {
                    append("[remove_resource] clicked at position ")
                    append("${state.currentPos}")
                }
            )
            deleteResource(state.currentItem.id())
            val newGalleryItems = state.galleryItems.toMutableList()
            newGalleryItems.removeAt(state.currentPos)
            if (newGalleryItems.isEmpty()) {
                intent {
                    postSideEffect(GallerySideEffect.NavigateBack)
                }
                return@intent
            }
            onTagsChanged()
            reduce {
                state.copy(galleryItems = newGalleryItems)
            }
        }
    }

    private suspend fun deleteResource(resource: ResourceId) {
        intent {
            Timber.d(LogTags.GALLERY_SCREEN, "deleting resource $resource")
            withContext(Dispatchers.IO) {
                val path = index.getPath(resource)
                Files.delete(path)
            }
            index.updateAll()

            postSideEffect(GallerySideEffect.NotifyResourceChange)
        }
    }

    fun onPlayButtonClick() = intent {
        postSideEffect(
            GallerySideEffect.ViewInExternalApp(
                index.getPath(state.currentItem.id())!!
            )
        )
    }

    fun onInfoFabClick() = intent {
        analytics.trackResInfo()
        Timber.d(
            LogTags.GALLERY_SCREEN,
            "[info_resource] clicked at position" +
                " ${container.stateFlow.value.currentPos}"
        )
        val path = index.getPath(state.currentItem.id())!!
        intent {
            postSideEffect(
                GallerySideEffect.ShowInfoAlert(
                    path = path,
                    resource = state.currentItem.resource,
                    metadata = state.currentItem.metadata
                )
            )
        }
    }

    fun onShareFabClick() = intent {
        analytics.trackResShare()
        Timber.d(
            LogTags.GALLERY_SCREEN,
            "[share_resource] clicked at position " +
                "${container.stateFlow.value.currentPos}"
        )
        val path = index.getPath(state.currentItem.id())!!
        if (state.currentItem.metadata is Metadata.Link) {
            val url = readText(path).getOrThrow()
            postSideEffect(GallerySideEffect.ShareLink(url))
            return@intent
        }
        postSideEffect(GallerySideEffect.ShareResource(path))
    }

    fun onSelectingChanged(enabled: Boolean) = intent {
        reduce {
            state.copy(selectingEnabled = enabled)
        }

        reduce {
            if (enabled) {
                state.copy(
                    selectedResources = state.selectedResources +
                        state.currentItem.id()
                )
            } else {
                state.copy(selectedResources = emptyList())
            }
        }
    }

    fun onOpenFabClick() = intent {
        analytics.trackResOpen()
        Timber.d(
            LogTags.GALLERY_SCREEN,
            "[open_resource] clicked at position " +
                "${container.stateFlow.value.currentPos}"
        )
        val id = state.currentItem.id()
        val path = index.getPath(id)!!
        if (state.currentItem.metadata is Metadata.Link) {
            val url = readText(path).getOrThrow()
            postSideEffect(GallerySideEffect.OpenLink(url))
            return@intent
        }
        postSideEffect(
            GallerySideEffect.ViewInExternalApp(
                index.getPath(
                    state.currentItem.id()
                )!!
            )
        )
    }

    fun onEditFabClick() = intent {
        analytics.trackResEdit()
        Timber.d(
            LogTags.GALLERY_SCREEN,
            "[edit_resource] clicked at position " +
                "${container.stateFlow.value.currentPos}"
        )
        val path = index.getPath(state.currentItem.id())!!
        postSideEffect(GallerySideEffect.EditResource(path))
    }

    fun onSelectBtnClick() = intent {
        val id = state.currentItem.id()
        val newSelectedList = state.selectedResources.toMutableList()
        val wasSelected = id in state.selectedResources
        if (wasSelected) {
            newSelectedList.remove(id)
        } else {
            newSelectedList.add(id)
        }

        reduce {
            state.copy(selectedResources = newSelectedList)
        }
    }

    fun onResume() = intent {
        checkResourceChanges(state.currentPos)
    }

    fun onTagsChanged() = intent {
        val tags = tagsStorage.getTags(state.currentItem.id())
        reduce {
            state.copy(tags = tags)
        }
    }

    fun onPageChanged(newPos: Int) = intent {
        if (state.galleryItems.isEmpty()) {
            return@intent
        }

        reduce {
            state.copy(currentPos = newPos)
        }
        postSideEffect(GallerySideEffect.AbortSelectAnimation)

        checkResourceChanges(newPos)
        val id = state.currentItem.id()
        val tags = tagsStorage.getTags(id)
        reduce {
            state.copy(tags = tags)
        }
        scoreWidgetController.displayScore()
    }

    fun onTagSelected(tag: Tag) {
        analytics.trackTagSelect()
        router.navigateTo(
            Screens.ResourcesScreenWithSelectedTag(
                container.stateFlow.value.rootAndFav,
                tag
            )
        )
    }

    fun onTagRemove(tag: Tag) = intent {
        intent {
            analytics.trackTagRemove()
            val id = state.currentItem.id()
            val tags = tagsStorage.getTags(id)
            val newTags = tags - tag
            reduce {
                state.copy(tags = newTags)
            }
            statsStorage.handleEvent(
                StatsEvent.TagsChanged(
                    id,
                    tags,
                    newTags
                )
            )
            Timber.d(
                LogTags.GALLERY_SCREEN,
                "setting new tags $newTags to $state.currentItem"
            )
            tagsStorage.setTags(id, newTags)
            tagsStorage.persist()
            postSideEffect(GallerySideEffect.NotifyTagsChanged)
        }
    }

    fun onEditTagsDialogBtnClick() = intent {
        analytics.trackTagsEdit()
        postSideEffect(
            GallerySideEffect.ShowEditTagsDialog(
                resource = state.currentItem.id(),
                resources = listOf(state.currentItem.id()),
                statsStorage = statsStorage,
                rootAndFav = state.rootAndFav,
                index = index,
                storage = tagsStorage
            )
        )
    }

    private fun checkResourceChanges(pos: Int) =
        intent {
            if (state.galleryItems.isEmpty()) {
                return@intent
            }
            val item = state.galleryItems[pos]
            val path = index.getPath(item.id())
                ?: let {
                    Timber.d("Resource ${item.id()} can't be found in the index")
                    invokeHandleGalleryExternalChangesUseCase()
                    return@intent
                }
            if (path.notExists()) {
                Timber.d("Resource ${item.id()} isn't stored by path $path")
                invokeHandleGalleryExternalChangesUseCase()
                return@intent
            }
            if (path.getLastModifiedTime() != item.resource.modified) {
                Timber.d("Index is not up-to-date regarding path $path")
                invokeHandleGalleryExternalChangesUseCase()
                return@intent
            }
        }

    private fun provideGalleryItems(
        resourcesIds: List<ResourceId>
    ): List<GalleryItem> =
        try {
            val allResources = index.allResources()
            resourcesIds
                .filter { allResources.keys.contains(it) }
                .map { id ->
                    val path = index.getPath(id)!!
                    val preview = previewStorage.retrieve(id).getOrThrow()
                    val metadata = metadataStorage.retrieve(id).getOrThrow()
                    val resource = allResources.getOrElse(id) {
                        throw NullPointerException("Resource not exist")
                    }
                    GalleryItem(resource, preview, metadata, path)
                }.toMutableList()
        } catch (e: Exception) {
            Timber.d("Can't provide gallery items")
            emptyList()
        }

    private fun invokeHandleGalleryExternalChangesUseCase() = intent {
        reduce {
            state.copy(progressState = ProgressState.Indexing)
        }
        index.updateAll()
        postSideEffect(GallerySideEffect.NotifyResourceChange)

        viewModelScope.launch {
            metadataStorage.busy.collect { busy ->
                if (!busy) cancel()
            }
        }.join()

        val newItems = provideGalleryItems(state.resourcesIds)
        if (newItems.isEmpty()) {
            postSideEffect(GallerySideEffect.NavigateBack)
            return@intent
        }

        reduce {
            state.copy(galleryItems = newItems)
        }
        reduce {
            state.copy(progressState = ProgressState.HideProgress)
        }
    }

    private suspend fun readText(source: Path): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val content = FileReader(source.toFile()).readText()
                Result.success(content)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun initStorages() = blockingIntent {
        analytics.trackScreen()
        Timber.d(
            LogTags.GALLERY_SCREEN,
            "first view attached in GalleryPresenter"
        )
        reduce {
            state.copy(progressState = ProgressState.ProvidingRootIndex)
        }
        index = indexRepo.provide(state.rootAndFav)
        messageFlow.onEach { message ->
            when (message) {
                is Message.KindDetectFailed ->
                    intent {
                        postSideEffect(
                            GallerySideEffect.ToastIndexFailedPath(
                                message.path
                            )
                        )
                    }
            }
        }.launchIn(viewModelScope)
        reduce {
            state.copy(progressState = ProgressState.ProvidingMetaDataStorage)
        }
        metadataStorage = metadataStorageRepo.provide(index)
        reduce {
            state.copy(progressState = ProgressState.ProvidingPreviewStorage)
        }
        previewStorage = previewStorageRepo.provide(index)
        reduce {
            state.copy(progressState = ProgressState.ProvidingDataStorage)
        }
        try {
            tagsStorage = tagsStorageRepo.provide(index)
            scoreStorage = scoreStorageRepo.provide(index)
        } catch (e: StorageException) {
            postSideEffect(
                GallerySideEffect.DisplayStorageException(
                    label = e.label,
                    messenger = e.msg
                )
            )
        }
        statsStorage = statsStorageRepo.provide(index)
        scoreWidgetController.init(scoreStorage)
        val galleryItems = provideGalleryItems(state.resourcesIds)
        viewModelScope.launch {
            val result = preferences.get(
                PreferenceKey.SortByScores
            )
            scoreWidgetController.setVisible(result)
        }
        reduce {
            state.copy(
                galleryItems = galleryItems,
                progressState = ProgressState.HideProgress
            )
        }
    }
}
