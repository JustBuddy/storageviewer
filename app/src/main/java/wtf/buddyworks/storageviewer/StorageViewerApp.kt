package wtf.buddyworks.storageviewer

import android.app.Application
import android.content.Context
import wtf.buddyworks.storageviewer.data.AppsRepository
import wtf.buddyworks.storageviewer.data.StorageRepository

class StorageViewerApp : Application() {
    val storage: StorageRepository by lazy { StorageRepository(this) }
    val apps: AppsRepository by lazy { AppsRepository(this) }
}

val Context.storageViewerApp: StorageViewerApp
    get() = applicationContext as StorageViewerApp
