package space.taran.arknavigator.mvp.presenter.adapter

import ru.terrakok.cicerone.Router
import space.taran.arknavigator.mvp.model.dao.ResourceId
import space.taran.arknavigator.mvp.model.dao.common.Preview
import space.taran.arknavigator.mvp.model.repo.ResourcesIndex
import space.taran.arknavigator.mvp.model.repo.TagsStorage
import space.taran.arknavigator.mvp.view.ResourcesView
import space.taran.arknavigator.mvp.view.item.FileItemView
import space.taran.arknavigator.navigation.Screens
import space.taran.arknavigator.utils.Sorting
import space.taran.arknavigator.utils.extension
import java.nio.file.Files

class ResourcesGridPresenter(
    val viewState: ResourcesView
) {
    private var resources = mutableListOf<ResourceId>()
    private lateinit var index: ResourcesIndex
    private lateinit var storage: TagsStorage
    private lateinit var router: Router
    var sorting = Sorting.DEFAULT
    var ascending: Boolean = true

    fun getCount() = resources.size

    fun bindView(view: FileItemView) {
        val resource = resources[view.position()]

        val path = index.getPath(resource)
            ?: throw java.lang.AssertionError("Resource to display must be indexed")

        view.setText(path.fileName.toString())

        if (Files.isDirectory(path)) {
            throw java.lang.AssertionError("Resource can't be a directory")
        }

        view.setIcon(Preview.provide(path))
    }

    fun onItemClick(pos: Int) {
        router.navigateTo(Screens.GalleryScreen(index, storage, resources, pos))
    }

    fun init(index: ResourcesIndex, storage: TagsStorage, router: Router) {
        this.index = index
        this.storage = storage
        this.router = router
    }

    fun updateResources(resources: List<ResourceId>) {
        this.resources = resources.toMutableList()
        push()
    }

    fun updateSorting(sorting: Sorting) {
        if (sorting == Sorting.DEFAULT)
            throw AssertionError("Not possible")
        this.sorting = sorting
        push()
    }

    fun updateAscending(ascending: Boolean) {
        this.ascending = ascending
        push()
    }

    private fun push() {
        when (sorting) {
            Sorting.NAME -> resources.sortBy { index.getPath(it)!!.fileName }
            Sorting.SIZE -> resources.sortBy { Files.size(index.getPath(it)!!) }
            Sorting.TYPE -> resources.sortBy { extension(index.getPath(it)!!) }
            Sorting.LAST_MODIFIED -> resources.sortBy { Files.getLastModifiedTime(index.getPath(it)!!) }
            Sorting.DEFAULT -> {
            }
        }
        if (!ascending)
            resources.reverse()
        viewState.updateAdapter()
    }
}