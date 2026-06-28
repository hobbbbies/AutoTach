package com.example.obdreader

import android.app.Application
import com.example.obdreader.data.ObdRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ObdReaderApp : Application() {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var obdRepository: ObdRepository
        private set

    override fun onCreate() {
        super.onCreate()
        obdRepository = ObdRepository(this, appScope)
    }
}
