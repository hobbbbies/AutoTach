package com.example.obdreader.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import com.example.obdreader.ObdReaderApp
import com.example.obdreader.data.ObdRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class ObdViewModel(application: Application) : AndroidViewModel(application) {

    enum class Screen { MAIN, CONNECT }

    private val repo = (application as ObdReaderApp).obdRepository

    private val _screen = MutableStateFlow(Screen.CONNECT)
    val screen: StateFlow<Screen> = _screen

    val errors: Flow<String> = repo.errors
    val devices = repo.devices
    val chosenDevice = repo.chosenDevice
    val rpm = repo.rpm

    val isBluetoothSupported: Boolean get() = repo.isBluetoothSupported
    val isBluetoothEnabled: Boolean get() = repo.isBluetoothEnabled

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() = repo.startScan()

    @android.annotation.SuppressLint("MissingPermission")
    fun setChosenDevice(device: BluetoothDevice) = repo.setChosenDevice(device)

    fun navigateTo(screen: Screen) {
        _screen.value = screen
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect() {
        repo.connect()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        repo.disconnect()
    }
}
