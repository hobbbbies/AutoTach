package com.example.obdreader.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.obdreader.bluetooth.BluetoothCommunicator
import com.example.obdreader.bluetooth.BluetoothScanner
import com.example.obdreader.bluetooth.ObdReader
import com.example.obdreader.interfaces.IObdReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ObdViewModel(application: Application) : AndroidViewModel(application) {

    enum class Screen { MAIN, CONNECT }

    private val _screen = MutableStateFlow(Screen.CONNECT)
    val screen: StateFlow<Screen> = _screen

    private val scanner = BluetoothScanner(application, viewModelScope)

    private var obdMessenger: IObdReader? = null

    val devices = scanner.devices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keeps flow active for 5s after UI leaves
            initialValue = emptySet())

    val chosenDevice = scanner.chosenDevice
        .stateIn(
            scope =viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keeps flow active for 5s after UI leaves
            initialValue = null
        )

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(): Unit {
        scanner.startScan()
    }


    val isBluetoothSupported: Boolean
        get() = scanner.isBluetoothSupported

    val isBluetoothEnabled: Boolean
        get() = scanner.isBluetoothEnabled

    fun setChosenDevice(device: BluetoothDevice) {
        scanner.setChosenDevice(device)
        obdMessenger = ObdReader(getApplication(), device)
    }
}
