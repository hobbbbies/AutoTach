package com.example.obdreader.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.CarText
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.obdreader.ObdReaderApp
import com.example.obdreader.bluetooth.ConnectionState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ObdCarScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val repo = (carContext.applicationContext as ObdReaderApp).obdRepository

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            combine(repo.rpm, repo.connectionState) { rpm, state -> rpm to state }
                .collect { invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        val rpm = repo.rpm.value
        val state = repo.connectionState.value

        val rpmText = if (rpm != null) "${rpm.toInt()} rpm" else "-- rpm"
        val statusText = when (state) {
            ConnectionState.DISCONNECTED -> "Connect on phone"
            ConnectionState.CONNECTING -> "Connecting…"
            ConnectionState.LINK_UP -> "Initializing…"
            ConnectionState.READY -> "Live"
        }

        val row = Row.Builder()
            .setTitle(rpmText)
            .addText(statusText)
            .build()

        val pane = Pane.Builder()
            .addRow(row)
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle(CarText.create("OBD"))
            .build()
    }
}
