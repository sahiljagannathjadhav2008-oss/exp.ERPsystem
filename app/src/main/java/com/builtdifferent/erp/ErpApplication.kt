package com.builtdifferent.erp

import android.app.Application
import com.builtdifferent.erp.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ErpApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** Long-lived scope for app-wide background work (seeding, future
     * scheduled backups). Uses SupervisorJob so one failed child doesn't
     * cancel the others. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            container.seedIfNeeded()
        }
    }
}
