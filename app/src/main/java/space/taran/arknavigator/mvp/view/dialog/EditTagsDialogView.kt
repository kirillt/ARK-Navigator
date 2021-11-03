package space.taran.arknavigator.mvp.view.dialog

import moxy.MvpView
import moxy.viewstate.strategy.AddToEndSingleStrategy
import moxy.viewstate.strategy.StateStrategyType
import space.taran.arknavigator.utils.Tag
import space.taran.arknavigator.utils.Tags

@StateStrategyType(AddToEndSingleStrategy::class)
interface EditTagsDialogView: MvpView {
    fun init()
    fun setRootTags(tags: List<Tag>)
    fun setResourceTags(tags: Tags)
    fun clearInput()
}