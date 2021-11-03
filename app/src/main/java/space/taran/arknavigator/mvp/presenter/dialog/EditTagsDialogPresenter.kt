package space.taran.arknavigator.mvp.presenter.dialog

import kotlinx.coroutines.launch
import moxy.MvpPresenter
import moxy.presenterScope
import space.taran.arknavigator.mvp.model.dao.ResourceId
import space.taran.arknavigator.mvp.model.repo.ResourcesIndex
import space.taran.arknavigator.mvp.model.repo.TagsStorage
import space.taran.arknavigator.mvp.view.dialog.EditTagsDialogView
import space.taran.arknavigator.utils.*
import space.taran.arknavigator.utils.Converters.Companion.tagsListFromString

class EditTagsDialogPresenter(
    private val resourceId: ResourceId,
    private val storage: TagsStorage,
    private val index: ResourcesIndex,
    private val onTagsChangedListener: (resource: ResourceId) -> Unit
): MvpPresenter<EditTagsDialogView>() {

    private var filter = ""

    override fun onFirstViewAttach() {
        viewState.init()
        viewState.setResourceTags(storage.getTags(resourceId))
        viewState.setRootTags(listRootTags())
    }

    fun onInputDone(input: String) = presenterScope.launch {
        val inputTags = Converters.tagsFromString(input, strict = false)
        if (inputTags.isEmpty()) return@launch

        val tags = storage.getTags(resourceId)
        val newTags = tags + inputTags
        storage.setTags(resourceId, newTags)

        updateTags()
        viewState.clearInput()
    }

    fun onResourceTagClick(tag: Tag) = presenterScope.launch {
        val newTags = listResourceTags() - tag
        storage.setTags(resourceId, newTags)

        updateTags()
    }

    fun onRootTagClick(tag: Tag) = presenterScope.launch  {
        val newTags = listResourceTags() + tag
        storage.setTags(resourceId, newTags)

        updateTags()
    }

    fun onInputChanged(input: String) {
        if (input.isEmpty()) {
            //this handler is called also when we reset input
            return
        }

        filter = tagsListFromString(input, strict = false).last()
        viewState.setRootTags(listRootTags())
    }

    private fun updateTags() {
        viewState.setResourceTags(listResourceTags())
        viewState.setRootTags(listRootTags())
        onTagsChangedListener(resourceId)
    }

    private fun listRootTags(): List<Tag> {
        val allTags = storage.groupTagsByResources(index.listAllIds())
            .values
            .flatten()
        val popularity = Popularity.calculate(allTags)
        val result = (popularity.keys - storage.getTags(resourceId))
            .filter {
                tag -> tag.startsWith(filter, true)
            }

        return result.sortedByDescending { popularity[it] }
    }

    private fun listResourceTags() = storage.getTags(resourceId)
}