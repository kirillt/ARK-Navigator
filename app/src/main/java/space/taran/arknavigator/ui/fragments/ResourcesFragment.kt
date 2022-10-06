package space.taran.arknavigator.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.launch
import moxy.MvpAppCompatFragment
import moxy.ktx.moxyPresenter
import moxy.presenterScope
import space.taran.arkfilepicker.onArkPathPicked
import space.taran.arknavigator.BuildConfig
import space.taran.arknavigator.R
import space.taran.arknavigator.databinding.FragmentResourcesBinding
import space.taran.arknavigator.mvp.model.repo.RootAndFav
import space.taran.arknavigator.mvp.presenter.ResourcesPresenter
import space.taran.arknavigator.mvp.presenter.adapter.tagsselector.QueryMode
import space.taran.arknavigator.mvp.view.ResourcesView
import space.taran.arknavigator.ui.App
import space.taran.arknavigator.ui.activity.MainActivity
import space.taran.arknavigator.ui.adapter.ResourcesRVAdapter
import space.taran.arknavigator.ui.adapter.TagsSelectorAdapter
import space.taran.arknavigator.ui.fragments.dialog.ConfirmationDialogFragment
import space.taran.arknavigator.ui.fragments.dialog.SortDialogFragment
import space.taran.arknavigator.ui.fragments.dialog.TagsSortDialogFragment
import space.taran.arknavigator.ui.fragments.utils.toast
import space.taran.arknavigator.ui.fragments.utils.toastFailedPaths
import space.taran.arknavigator.ui.view.StackedToasts
import space.taran.arknavigator.utils.FullscreenHelper
import space.taran.arknavigator.utils.LogTags.RESOURCES_SCREEN
import space.taran.arknavigator.utils.Tag
import space.taran.arknavigator.utils.extensions.closeKeyboard
import space.taran.arknavigator.utils.extensions.placeCursorToEnd
import space.taran.arknavigator.utils.extensions.showKeyboard
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.math.abs

// `root` is used for querying tags storage and resources index,
//       if it is `null`, then resources from all roots are taken
//                        and tags storage for every particular resource
//                        is determined dynamically
//
// `path` is used for filtering resources' paths
//       if it is `null`, then no filtering is performed
//       (recommended instead of passing same value for `path` and `root)
class ResourcesFragment : MvpAppCompatFragment(), ResourcesView {

    val presenter by moxyPresenter {
        ResourcesPresenter(
            requireArguments()[ROOT_AND_FAV_KEY] as RootAndFav,
            requireArguments().getString(SELECTED_TAG_KEY)
        ).apply {
            Log.d(RESOURCES_SCREEN, "creating ResourcesPresenter")
            App.instance.appComponent.inject(this)
        }
    }

    private lateinit var binding: FragmentResourcesBinding
    private var resourcesAdapter: ResourcesRVAdapter? = null
    private lateinit var stackedToasts: StackedToasts

    private val frameTop by lazy {
        val loc = IntArray(2)
        binding.root.getLocationOnScreen(loc)
        loc[1]
    }
    private val frameHeight by lazy { binding.root.height }

    private var selectorHeight: Float = 0.3f // ratio

    private var selectorDragStartBias: Float = -1f
    private var selectorDragStartTime: Long = -1

    private var tagsSelectorAdapter: TagsSelectorAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        Log.d(RESOURCES_SCREEN, "inflating layout for ResourcesFragment")
        binding = FragmentResourcesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(RESOURCES_SCREEN, "view created in ResourcesFragment")
        super.onViewCreated(view, savedInstanceState)

        App.instance.appComponent.inject(this)
    }

    override fun init() = with(binding) {
        Log.d(RESOURCES_SCREEN, "initializing ResourcesFragment")

        initResultListeners()
        initMenuListeners()
        stackedToasts = StackedToasts(binding.rvToasts, lifecycleScope)

        FullscreenHelper.setStatusBarVisibility(true, requireActivity().window)
        (activity as MainActivity).setSelectedTab(R.id.page_tags)
        (requireActivity() as MainActivity).setBottomNavigationVisibility(true)

        resourcesAdapter = ResourcesRVAdapter(presenter.gridPresenter)
        rvResources.adapter = resourcesAdapter
        rvResources.setItemViewCacheSize(0)
        rvResources.layoutManager = GridLayoutManager(context, 3)
        tagsSelectorAdapter = TagsSelectorAdapter(
            this@ResourcesFragment,
            binding,
            presenter.tagsSelectorPresenter
        ).also {
            App.instance.appComponent.inject(it)
        }

        layoutDragHandler.setOnTouchListener(::dragHandlerTouchListener)
        etTagsFilter.doAfterTextChanged {
            presenter.tagsSelectorPresenter.onFilterChanged(it.toString())
        }
        switchKind.setOnCheckedChangeListener { _, checked ->
            presenter.tagsSelectorPresenter.onKindTagsToggle(checked)
        }
        btnClear.setOnClickListener {
            presenter.tagsSelectorPresenter.onClearClick()
        }
        btnTagsSorting.setOnClickListener {
            TagsSortDialogFragment
                .newInstance()
                .show(childFragmentManager, null)
        }

        this@ResourcesFragment
            .requireActivity()
            .onBackPressedDispatcher
            .addCallback(this@ResourcesFragment) {
                presenter.onBackClick()
            }
        return@with
    }

    override fun onResume() {
        Log.d(RESOURCES_SCREEN, "resuming in ResourcesFragment")
        super.onResume()
        updateDragHandlerBias()
    }

    override fun setToolbarTitle(title: String) {
        binding.actionBar.tvTitle.text = title
    }

    override fun setKindTagsEnabled(enabled: Boolean) {
        binding.switchKind.toggleSwitchSilent(enabled)
    }

    override fun setProgressVisibility(isVisible: Boolean, withText: String) {
        binding.layoutProgress.apply {
            root.isVisible = isVisible
            (activity as MainActivity).setBottomNavigationEnabled(!isVisible)

            if (withText.isNotEmpty()) {
                progressText.setVisibilityAndLoadingStatus(View.VISIBLE)
                progressText.loadingText = withText
            } else {
                progressText.setVisibilityAndLoadingStatus(View.GONE)
            }
        }
    }

    override fun updateAdapter() {
        resourcesAdapter?.notifyDataSetChanged()
    }

    override fun updateMenu() = with(binding) {
        val queryMode = presenter.tagsSelectorPresenter.queryMode
        val normalVisibility =
            if (queryMode != QueryMode.NORMAL) View.VISIBLE else View.INVISIBLE
        val focusVisibility =
            if (queryMode != QueryMode.FOCUS) View.VISIBLE else View.INVISIBLE
        actionBar.btnNormalMode.visibility = normalVisibility
        actionBar.btnFocusMode.visibility = focusVisibility
        return@with
    }

    override fun setSelectingEnabled(enabled: Boolean) = with(binding.actionBar) {
        tvTitle.isVisible = !enabled
        tvSelectedOf.isVisible = enabled
        ivDisableSelectionMode.isVisible = enabled
        ivUseSelected.isVisible = enabled

        return@with
    }

    override fun setSelectingCount(selected: Int, all: Int) {
        binding.actionBar.tvSelectedOf.text = "$selected of $all"
    }

    override fun setTagsFilterEnabled(enabled: Boolean) {
        binding.layoutInput.isVisible = enabled
        binding.cgTagsChecked.isVisible = enabled
        if (enabled) {
            binding.etTagsFilter.placeCursorToEnd()
            binding.etTagsFilter.showKeyboard()
        } else
            binding.etTagsFilter.closeKeyboard()
    }

    override fun setTagsFilterText(filter: String) {
        binding.etTagsFilter.setText(filter)
    }

    override fun drawTags() {
        tagsSelectorAdapter?.drawTags()
    }

    override fun toastResourcesSelected(selected: Int) {
        if (isFragmentVisible())
            toast(R.string.toast_resources_selected, selected)
    }

    override fun toastResourcesSelectedFocusMode(selected: Int, hidden: Int) {
        if (isFragmentVisible())
            toast(
                R.string.toast_resources_selected_focus_mode,
                selected,
                hidden
            )
    }

    override fun toastPathsFailed(failedPaths: List<Path>) =
        toastFailedPaths(failedPaths)

    override fun clearStackedToasts() {
        stackedToasts.clearToasts()
    }

    override fun onSelectingChanged(enabled: Boolean) {
        binding.rvResources.recycledViewPool.clear()
        resourcesAdapter?.onSelectingChanged(enabled)
    }

    override fun shareResources(resources: List<Path>) {
        val fileUris = resources.map {
            FileProvider.getUriForFile(
                requireContext(),
                BuildConfig.APPLICATION_ID + ".provider",
                it.toFile()
            )
        }
        val intent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            type = "file/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fileUris))
        }
        startActivity(
            Intent.createChooser(
                intent,
                getString(R.string.share_resources_with)
            )
        )
    }

    private fun initMenuListeners() = with(binding) {
        actionBar.ivDisableSelectionMode.setOnClickListener {
            presenter.gridPresenter.onSelectingChanged(false)
        }
        actionBar.ivUseSelected.setOnClickListener {
            setupAndShowSelectedResourcesMenu(it)
        }
        actionBar.btnSort.setOnClickListener {
            val dialog = SortDialogFragment.newInstance()
            dialog.show(childFragmentManager, null)
        }
        actionBar.btnNormalMode.setOnClickListener {
            presenter.tagsSelectorPresenter.onQueryModeChanged(QueryMode.NORMAL)
        }
        actionBar.btnFocusMode.setOnClickListener {
            presenter.tagsSelectorPresenter.onQueryModeChanged(QueryMode.FOCUS)
        }
    }

    private fun initResultListeners() {
        childFragmentManager.onArkPathPicked(
            this,
            MOVE_SELECTED_REQUEST_KEY
        ) { path ->
            val selectedSize = presenter.gridPresenter.resources
                .filter { it.isSelected }
                .size
            val description = "$selectedSize " +
                getString(R.string.resources_will_be_moved)
            ConfirmationDialogFragment
                .newInstance(
                    getString(R.string.are_you_sure),
                    description,
                    getString(R.string.yes),
                    getString(R.string.no),
                    MOVE_CONFIRMATION_REQUEST_KEY,
                    bundleOf(MOVE_TO_PATH_KEY to path.toString())
                )
                .show(parentFragmentManager, null)
        }

        childFragmentManager.onArkPathPicked(
            this,
            COPY_SELECTED_REQUEST_KEY
        ) {
            presenter.onCopySelectedResourcesClicked(it)
        }

        setFragmentResultListener(
            MOVE_CONFIRMATION_REQUEST_KEY
        ) { _, bundle ->
            presenter.onMoveSelectedResourcesClicked(
                Path(
                    bundle
                        .getString(MOVE_TO_PATH_KEY)!!
                )
            )
        }

        setFragmentResultListener(
            DELETE_CONFIRMATION_REQUEST_KEY
        ) { _, _ ->
            presenter.onRemoveSelectedResourcesClicked()
        }

        setFragmentResultListener(
            GalleryFragment.REQUEST_TAGS_CHANGED_KEY
        ) { _, _ ->
            presenter.apply {
                presenterScope.launch {
                    onResourcesOrTagsChanged()
                }
            }
        }

        setFragmentResultListener(
            GalleryFragment.REQUEST_RESOURCES_CHANGED_KEY
        ) { _, _ ->
            presenter.apply {
                presenterScope.launch {
                    onResourcesOrTagsChanged()
                }
            }
        }
    }

    private fun dragHandlerTouchListener(view: View, event: MotionEvent): Boolean {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                val layoutParams = binding.ivDragHandler.layoutParams
                    as ConstraintLayout.LayoutParams
                selectorDragStartBias = layoutParams.verticalBias
                selectorDragStartTime = SystemClock.uptimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                view.performClick()

                val travelTime =
                    SystemClock.uptimeMillis() - selectorDragStartTime
                val travelDelta = selectorDragStartBias - (1f - selectorHeight)
                val travelSpeed = 100f * travelDelta / (travelTime / 1000f)
                Log.d(
                    RESOURCES_SCREEN,
                    "draggable bar of tags selector was moved:"
                )
                Log.d(RESOURCES_SCREEN, "delta=${100f * travelDelta}%")
                Log.d(RESOURCES_SCREEN, "time=${travelTime}ms")
                Log.d(RESOURCES_SCREEN, "speed=$travelSpeed%/sec")

                if (travelTime > DRAG_TRAVEL_TIME_THRESHOLD &&
                    abs(travelDelta) > DRAG_TRAVEL_DELTA_THRESHOLD &&
                    abs(travelSpeed) > DRAG_TRAVEL_SPEED_THRESHOLD
                ) {
                    selectorHeight = if (travelDelta > 0f) {
                        presenter.tagsSelectorPresenter.onFilterToggle(true)
                        1f
                    } else {
                        presenter.tagsSelectorPresenter.onFilterToggle(false)
                        0f
                    }
                    updateDragHandlerBias()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val distanceFromTop = event.rawY - frameTop
                selectorHeight = if (distanceFromTop < 0f) {
                    presenter.tagsSelectorPresenter.onFilterToggle(true)
                    1f
                } else if (distanceFromTop > frameHeight) {
                    0f
                } else {
                    presenter.tagsSelectorPresenter.onFilterToggle(false)
                    1f - distanceFromTop / frameHeight
                }

                val newBias = updateVerticalBias(view)

                val historySize = event.historySize
                if (historySize >= 2) {
                    val oldest = event.getHistoricalY(historySize - 2)
                    val old = event.getHistoricalY(historySize - 1)

                    val turnedFromDownToUp = event.y < old && old > oldest
                    val turnedFromUpToDown = event.y > old && old < oldest

                    if (turnedFromDownToUp || turnedFromUpToDown) {
                        selectorDragStartBias = newBias
                        selectorDragStartTime = SystemClock.uptimeMillis()
                    }
                }
            }
        }
        return true
    }

    private fun updateVerticalBias(view: View): Float {
        val layoutParams = view.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.verticalBias = 1f - selectorHeight
        view.layoutParams = layoutParams

        return layoutParams.verticalBias
    }

    private fun updateDragHandlerBias() {
        updateVerticalBias(binding.layoutDragHandler)
    }

    /**
     * ResourcesFragment can be overlapped by GalleryFragment
     */
    private fun isFragmentVisible(): Boolean {
        return parentFragmentManager.fragments.find { f ->
            f is GalleryFragment
        } == null
    }

    override fun toastIndexFailedPath(path: Path) {
        stackedToasts.toast(path)
    }

    companion object {
        const val MOVE_SELECTED_REQUEST_KEY = "moveSelected"
        const val COPY_SELECTED_REQUEST_KEY = "copySelected"
        private const val MOVE_CONFIRMATION_REQUEST_KEY = "moveConfirm"
        const val DELETE_CONFIRMATION_REQUEST_KEY = "deleteConfirm"
        private const val MOVE_TO_PATH_KEY = "moveToPath"

        private const val DRAG_TRAVEL_TIME_THRESHOLD = 30 // milliseconds
        private const val DRAG_TRAVEL_DELTA_THRESHOLD = 0.1 // ratio
        private const val DRAG_TRAVEL_SPEED_THRESHOLD =
            150 // percents per second
        private const val ROOT_AND_FAV_KEY = "rootAndFav"
        private const val SELECTED_TAG_KEY = "selectedTag"

        fun newInstance(
            rootAndFav: RootAndFav
        ) = ResourcesFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ROOT_AND_FAV_KEY, rootAndFav)
            }
        }

        fun newInstanceWithSelectedTag(
            rootAndFav: RootAndFav,
            tag: Tag
        ) = ResourcesFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ROOT_AND_FAV_KEY, rootAndFav)
                putString(SELECTED_TAG_KEY, tag)
            }
        }
    }
}
