package com.example.obdreader.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.obdreader.ObdReaderApp
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ObdNavCarScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val repo = (carContext.applicationContext as ObdReaderApp).obdRepository
    private val surfaceCallback = ObdSurface(carContext, repo)
    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)

        owner.lifecycleScope.launch {
            combine(repo.rpm, repo.connectionState) { rpm, state -> rpm to state }
                .collect { surfaceCallback.render() }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
    }

    override fun onGetTemplate(): Template {
        return NavigationTemplate.Builder()
            .setActionStrip(
            ActionStrip.Builder()
                .addAction(Action.APP_ICON)
                .build()
            ). build()
    }
}
