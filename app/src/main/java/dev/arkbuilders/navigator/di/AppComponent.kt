package dev.arkbuilders.navigator.di

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import dev.arkbuilders.arkfilepicker.folders.FoldersRepo
import dev.arkbuilders.navigator.analytics.AnalyticsModule
import dev.arkbuilders.navigator.data.StorageBackup
import dev.arkbuilders.navigator.data.preferences.Preferences
import dev.arkbuilders.navigator.di.modules.AppModule
import dev.arkbuilders.navigator.di.modules.CiceroneModule
import dev.arkbuilders.navigator.di.modules.DispatcherModule
import dev.arkbuilders.navigator.di.modules.RepoModule
import dev.arkbuilders.navigator.presentation.App
import dev.arkbuilders.navigator.presentation.dialog.ExplainPermsDialog
import dev.arkbuilders.navigator.presentation.dialog.RootPickerDialogFragment
import dev.arkbuilders.navigator.presentation.dialog.edittags.EditTagsDialogPresenter
import dev.arkbuilders.navigator.presentation.dialog.rootsscan.RootsScanDialogPresenter
import dev.arkbuilders.navigator.presentation.dialog.sort.SortDialogPresenter
import dev.arkbuilders.navigator.presentation.dialog.tagssort.TagsSortDialogFragment
import dev.arkbuilders.navigator.presentation.screen.folders.FoldersFragment
import dev.arkbuilders.navigator.presentation.screen.gallery.GalleryFragment
import dev.arkbuilders.navigator.presentation.screen.main.MainActivity
import dev.arkbuilders.navigator.presentation.screen.resources.ResourcesFragment
import dev.arkbuilders.navigator.presentation.screen.resources.ResourcesPresenter
import dev.arkbuilders.navigator.presentation.screen.resources.adapter.FileItemViewHolder
import dev.arkbuilders.navigator.presentation.screen.resources.adapter.ResourcesGridPresenter
import dev.arkbuilders.navigator.presentation.screen.settings.SettingsFragment
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AppModule::class,
        CiceroneModule::class,
        RepoModule::class,
        DispatcherModule::class,
        AnalyticsModule::class
    ]
)
interface AppComponent {
    fun arkBackup(): StorageBackup
    fun preferences(): Preferences

    fun inject(mainActivity: MainActivity)
    fun inject(foldersFragment: FoldersFragment)
    fun inject(resourcesPresenter: ResourcesPresenter)
    fun inject(resourcesFragment: ResourcesFragment)
    fun inject(galleryFragment: GalleryFragment)

    fun inject(settingsFragment: SettingsFragment)
    fun inject(resourcesGridPresenter: ResourcesGridPresenter)
    fun inject(editTagsDialogPresenter: EditTagsDialogPresenter)
    fun inject(fileItemViewHolder: FileItemViewHolder)
    fun inject(sortDialogPresenter: SortDialogPresenter)
    fun inject(tagsSortDialogFragment: TagsSortDialogFragment)
    fun inject(rootPickerDialogFragment: RootPickerDialogFragment)
    fun inject(explainPermsDialog: ExplainPermsDialog)
    fun inject(rootsScanDialogPresenter: RootsScanDialogPresenter)

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance app: App,
            @BindsInstance context: Context,
            @BindsInstance foldersRepo: FoldersRepo
        ): AppComponent
    }
}
