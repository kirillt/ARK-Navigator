package dev.arkbuilders.navigator.presentation.dialog.edittags

import moxy.MvpView
import moxy.viewstate.strategy.AddToEndSingleStrategy
import moxy.viewstate.strategy.SkipStrategy
import moxy.viewstate.strategy.StateStrategyType
import space.taran.arklib.domain.tags.Tag
import space.taran.arklib.domain.tags.Tags

@StateStrategyType(AddToEndSingleStrategy::class)
interface EditTagsDialogView : MvpView {
    fun init()
    fun showKeyboardAndView()
    fun hideSortingBtn()
    fun setQuickTags(tags: List<Tag>)
    fun setResourceTags(tags: Tags)
    fun setInput(input: String)
    @StateStrategyType(SkipStrategy::class)
    fun dismissDialog()
}